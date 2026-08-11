package com.Jolie.career_toolkit.resume;

import com.Jolie.career_toolkit.IntegrationTestBase;
import com.Jolie.career_toolkit.block.Block;
import com.Jolie.career_toolkit.block.BlockRepository;
import com.Jolie.career_toolkit.block.BlockType;
import com.Jolie.career_toolkit.user.AppUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class ResumeVersionTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockRepository blockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;
    private UUID skillBlockId;
    private UUID projectBlockId;

    @BeforeEach
    void setUp() {
        me = createUser(uniqueEmail("resume"));
        skillBlockId = blockRepository.save(
                new Block(me.id(), BlockType.SKILL, "Java", "五年後端開發經驗")).getId();
        projectBlockId = blockRepository.save(
                new Block(me.id(), BlockType.PROJECT, "求職工具", "Spring Boot + React")).getId();
        entityManager.flush();
    }

    // ---------- 鎖定與快照：這一組是 P4 存在的理由 ----------

    /**
     * 這是整個 P4 最重要的測試。
     *
     * 沒有快照機制的話：改一個 block，所有過去的履歷版本會「靜默」跟著變。
     * 「我三月寄給台積電的版本」會在你不知情的狀況下變成謊言，而且無法還原。
     */
    @Test
    void lockedVersion_shouldNotChangeWhenUnderlyingBlockIsEdited() throws Exception {
        String versionId = createVersion("投台積電用");
        addBlock(versionId, skillBlockId);

        mockMvc.perform(post("/api/resumes/{id}/lock", versionId).with(user(me)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.lockedAt").exists());

        entityManager.flush();
        entityManager.clear();

        // 鎖定之後把底層積木整個改掉
        Block block = blockRepository.findById(skillBlockId).orElseThrow();
        block.updateContent("完全不同的新內容");
        entityManager.flush();
        entityManager.clear();

        // 履歷版本必須維持鎖定當下的樣子
        mockMvc.perform(get("/api/resumes/{id}/blocks", versionId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("五年後端開發經驗"));
    }

    @Test
    void lockedVersion_shouldRejectEveryMutationWith409() throws Exception {
        String versionId = createVersion("已鎖定");
        addBlock(versionId, skillBlockId);
        mockMvc.perform(post("/api/resumes/{id}/lock", versionId).with(user(me)).with(csrf()))
                .andExpect(status().isOk());

        // 409 而不是 400：請求本身沒問題，是資源的狀態不允許
        mockMvc.perform(post("/api/resumes/{id}/blocks", versionId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockId":"%s"}
                                """.formatted(projectBlockId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resume version locked"));

        mockMvc.perform(delete("/api/resumes/{id}/blocks/{b}", versionId, skillBlockId)
                        .with(user(me)).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/resumes/{id}/order", versionId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockIds":["%s"]}
                                """.formatted(skillBlockId)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/resumes/{id}", versionId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"想改名"}
                                """))
                .andExpect(status().isConflict());
    }

    /** 鎖定的版本要改就複製一份——複製出來的必須是「活的」，不能一出生就被凍結。 */
    @Test
    void clone_shouldProduceAnEditableCopyWithoutSnapshots() throws Exception {
        String original = createVersion("原始版");
        addBlock(original, skillBlockId);
        mockMvc.perform(post("/api/resumes/{id}/lock", original).with(user(me)).with(csrf()))
                .andExpect(status().isOk());
        entityManager.flush();

        String copyBody = mockMvc.perform(post("/api/resumes/{id}/clone", original)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"改給聯發科"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.parentId").value(original))
                .andReturn().getResponse().getContentAsString();

        String copyId = extractId(copyBody);
        entityManager.flush();
        entityManager.clear();

        // 複製出來的是活的：改底層積木會跟著變
        Block block = blockRepository.findById(skillBlockId).orElseThrow();
        block.updateContent("複製之後改的內容");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/resumes/{id}/blocks", copyId).with(user(me)))
                .andExpect(jsonPath("$[0].content").value("複製之後改的內容"));

        // 原始版仍然凍結
        mockMvc.perform(get("/api/resumes/{id}/blocks", original).with(user(me)))
                .andExpect(jsonPath("$[0].content").value("五年後端開發經驗"));
    }

    // ---------- 客製化措辭 ----------

    /**
     * content_override 的意義：同一段經歷在不同履歷裡換個說法，
     * 而不用複製出第二個 block（複製之後兩份就會各自漂移，再也對不起來）。
     */
    @Test
    void override_shouldAffectOnlyThisVersion() throws Exception {
        String versionA = createVersion("版本 A");
        String versionB = createVersion("版本 B");
        addBlock(versionA, skillBlockId);
        addBlock(versionB, skillBlockId);

        mockMvc.perform(put("/api/resumes/{id}/blocks/{b}/override", versionA, skillBlockId)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"針對這家改過的說法"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("針對這家改過的說法"))
                .andExpect(jsonPath("$[0].overridden").value(true));

        // 版本 B 不受影響
        mockMvc.perform(get("/api/resumes/{id}/blocks", versionB).with(user(me)))
                .andExpect(jsonPath("$[0].content").value("五年後端開發經驗"))
                .andExpect(jsonPath("$[0].overridden").value(false));

        // 原始積木也不受影響
        entityManager.flush();
        entityManager.clear();
        assertThat(blockRepository.findById(skillBlockId).orElseThrow().getContent())
                .isEqualTo("五年後端開發經驗");
    }

    @Test
    void emptyOverride_shouldFallBackToOriginalContent() throws Exception {
        String versionId = createVersion("測試清除");
        addBlock(versionId, skillBlockId);

        setOverride(versionId, skillBlockId, "先改一次");
        // 空字串代表「清除客製化，回到原文」——跟不呼叫這個端點是不同的意圖
        mockMvc.perform(put("/api/resumes/{id}/blocks/{b}/override", versionId, skillBlockId)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(jsonPath("$[0].content").value("五年後端開發經驗"))
                .andExpect(jsonPath("$[0].overridden").value(false));
    }

    // ---------- 排序 ----------

    @Test
    void reorder_shouldPersist() throws Exception {
        String versionId = createVersion("排序測試");
        addBlock(versionId, skillBlockId);
        addBlock(versionId, projectBlockId);

        mockMvc.perform(get("/api/resumes/{id}/blocks", versionId).with(user(me)))
                .andExpect(jsonPath("$[0].blockId").value(skillBlockId.toString()));

        mockMvc.perform(put("/api/resumes/{id}/order", versionId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockIds":["%s","%s"]}
                                """.formatted(projectBlockId, skillBlockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].blockId").value(projectBlockId.toString()))
                .andExpect(jsonPath("$[1].blockId").value(skillBlockId.toString()));

        entityManager.flush();
        entityManager.clear();

        // 重新讀一次，確認真的存進去了而不是只在回應裡排好看的
        mockMvc.perform(get("/api/resumes/{id}/blocks", versionId).with(user(me)))
                .andExpect(jsonPath("$[0].blockId").value(projectBlockId.toString()));
    }

    @Test
    void addingTheSameBlockTwice_shouldNotDuplicate() throws Exception {
        String versionId = createVersion("重複測試");
        addBlock(versionId, skillBlockId);
        addBlock(versionId, skillBlockId);

        // 複合主鍵 (version, block) 讓「同一個積木出現兩次」在資料庫層面就不可能
        mockMvc.perform(get("/api/resumes/{id}/blocks", versionId).with(user(me)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- 匯出 ----------

    @Test
    void markdownExport_shouldMatchTheRenderedContent() throws Exception {
        String versionId = createVersion("匯出測試");
        addBlock(versionId, skillBlockId);
        addBlock(versionId, projectBlockId);
        setOverride(versionId, skillBlockId, "客製化過的技能描述");

        String markdown = mockMvc.perform(get("/api/resumes/{id}/export.md", versionId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(markdown)
                .contains("# 匯出測試")
                .contains("### Java")
                // 匯出走的是跟預覽同一條渲染路徑，所以客製化的內容一定會出現
                .contains("客製化過的技能描述")
                .contains("### 求職工具")
                // 被客製化蓋掉的原文不該出現
                .doesNotContain("五年後端開發經驗");
    }

    // ---------- 跨帳號隔離 ----------

    @Test
    void anotherUser_shouldGet404OnEveryOperation() throws Exception {
        String versionId = createVersion("我的履歷");
        addBlock(versionId, skillBlockId);
        AppUserDetails other = createUser(uniqueEmail("intruder"));

        mockMvc.perform(get("/api/resumes/{id}", versionId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes/{id}/blocks", versionId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes/{id}/export.md", versionId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/resumes/{id}/lock", versionId).with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/resumes/{id}", versionId).with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_shouldOnlyContainOwnVersions() throws Exception {
        createVersion("我的 1");
        createVersion("我的 2");

        AppUserDetails other = createUser(uniqueEmail("other"));
        mockMvc.perform(post("/api/resumes").with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"別人的"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/resumes").with(user(me)))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/resumes").with(user(other)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** 不能把別人的積木塞進自己的履歷。 */
    @Test
    void cannotAddAnotherUsersBlock() throws Exception {
        AppUserDetails other = createUser(uniqueEmail("other"));
        UUID theirBlock = blockRepository.save(
                new Block(other.id(), BlockType.SKILL, "別人的技能", "內容")).getId();
        entityManager.flush();

        String versionId = createVersion("我的履歷");

        mockMvc.perform(post("/api/resumes/{id}/blocks", versionId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockId":"%s"}
                                """.formatted(theirBlock)))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String createVersion(String label) throws Exception {
        String body = mockMvc.perform(post("/api/resumes").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"%s"}
                                """.formatted(label)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private void addBlock(String versionId, UUID blockId) throws Exception {
        mockMvc.perform(post("/api/resumes/{id}/blocks", versionId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockId":"%s"}
                                """.formatted(blockId)))
                .andExpect(status().isOk());
    }

    private void setOverride(String versionId, UUID blockId, String content) throws Exception {
        mockMvc.perform(put("/api/resumes/{id}/blocks/{b}/override", versionId, blockId)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(content)))
                .andExpect(status().isOk());
    }

    private static String extractId(String json) {
        return json.replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }
}
