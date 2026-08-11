package com.Jolie.career_toolkit.portfolio;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class PortfolioAndPresetsTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockRepository blockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;

    @BeforeEach
    void setUp() {
        me = createUser(uniqueEmail("portfolio"));
    }

    // ---------- 作品集 ----------

    @Test
    void newProject_shouldDefaultToPrivate() throws Exception {
        // 公開必須是明確的動作，不能是忘了關的預設值
        mockMvc.perform(post("/api/projects").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"求職工具"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPublic").value(false));
    }

    /**
     * 這是 P6 最重要的測試。
     *
     * 公開作品集頁不需要登入，所以「只回 is_public = true」這條規則
     * 是唯一擋在私人專案與全世界之間的東西。
     */
    @Test
    void publicPortfolio_shouldOnlyExposePublicProjects() throws Exception {
        createProject("公開的專案", true);
        createProject("私人的專案", false);
        entityManager.flush();

        // 未登入也看得到
        mockMvc.perform(get("/api/public/portfolio/{userId}", me.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("公開的專案"));
    }

    /**
     * 公開端點回傳的欄位必須是「明確列出來的」，不能重用內部 DTO。
     * 之後有人在 Project 加了私人欄位，這個測試會在那一刻就失敗。
     */
    @Test
    void publicPortfolio_shouldNotLeakInternalFields() throws Exception {
        createProject("公開的專案", true);
        entityManager.flush();

        String body = mockMvc.perform(get("/api/public/portfolio/{userId}", me.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("isPublic")     // 內部狀態不需要公開
                .doesNotContain("sortOrder")
                .doesNotContain("blockId")
                .doesNotContain("userId")
                .doesNotContain(me.email());    // 絕不能洩漏使用者本人的資訊
    }

    @Test
    void publicPortfolio_ofUserWithNothingPublic_shouldBeEmptyNotError() throws Exception {
        createProject("私人的", false);
        entityManager.flush();

        // 回空陣列而不是 404：404 會告訴對方「這個 userId 不存在」，
        // 空陣列什麼也沒說
        mockMvc.perform(get("/api/public/portfolio/{userId}", me.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void privateProjectList_shouldRequireLogin() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherUsersProject_shouldReturn404() throws Exception {
        String id = createProject("我的專案", false);
        AppUserDetails other = createUser(uniqueEmail("other"));

        mockMvc.perform(get("/api/projects/{id}", id).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/projects/{id}", id).with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- 題型與題組 ----------

    @Test
    void questionTypes_shouldBeSeeded() throws Exception {
        mockMvc.perform(get("/api/question-types").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SELF_INTRO')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'WHY_US')]").exists())
                // 提示才是準備的時候最需要的東西
                .andExpect(jsonPath("$[0].hint").exists());
    }

    /**
     * 題型對應的核心：選一個題型 → 依序組出該題組的積木 → 直接對照字數上限。
     */
    @Test
    void assemblingPreset_shouldJoinBlocksInOrderAndCountChars() throws Exception {
        UUID first = blockRepository.save(
                new Block(me.id(), BlockType.ANSWER_SNIPPET, "開場", "我是後端工程師")).getId();
        UUID second = blockRepository.save(
                new Block(me.id(), BlockType.ANSWER_SNIPPET, "經歷", "五年 Java 經驗")).getId();
        entityManager.flush();

        String presetId = createPreset("自我介紹 300 字版", 300);

        addBlockToPreset(presetId, first);
        mockMvc.perform(post("/api/presets/{id}/blocks", presetId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockId":"%s"}
                                """.formatted(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andExpect(jsonPath("$.parts[0].title").value("開場"))
                .andExpect(jsonPath("$.parts[1].title").value("經歷"))
                // 「我是後端工程師」7 + 兩個換行 2 + 「五年 Java 經驗」10（含兩個空格）= 19
                .andExpect(jsonPath("$.charCount").value(19))
                .andExpect(jsonPath("$.targetCharLimit").value(300));
    }

    @Test
    void reorderingPreset_shouldChangeAssembledOrder() throws Exception {
        UUID a = blockRepository.save(
                new Block(me.id(), BlockType.ANSWER_SNIPPET, "A", "aaa")).getId();
        UUID b = blockRepository.save(
                new Block(me.id(), BlockType.ANSWER_SNIPPET, "B", "bbb")).getId();
        entityManager.flush();

        String presetId = createPreset("排序測試", null);
        addBlockToPreset(presetId, a);
        addBlockToPreset(presetId, b);

        mockMvc.perform(put("/api/presets/{id}/order", presetId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockIds":["%s","%s"]}
                                """.formatted(b, a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].title").value("B"))
                .andExpect(jsonPath("$.text").value("bbb\n\naaa"));
    }

    @Test
    void cannotAddAnotherUsersBlockToPreset() throws Exception {
        AppUserDetails other = createUser(uniqueEmail("other"));
        UUID theirBlock = blockRepository.save(
                new Block(other.id(), BlockType.ANSWER_SNIPPET, "別人的", "內容")).getId();
        entityManager.flush();

        String presetId = createPreset("我的題組", null);

        mockMvc.perform(post("/api/presets/{id}/blocks", presetId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockId":"%s"}
                                """.formatted(theirBlock)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersPreset_shouldReturn404() throws Exception {
        String presetId = createPreset("我的題組", null);
        AppUserDetails other = createUser(uniqueEmail("other"));

        mockMvc.perform(get("/api/presets/{id}/assembled", presetId).with(user(other)))
                .andExpect(status().isNotFound());
    }

    /**
     * P3 的面試檢討與 P6 的準備之間的那條線。
     * 沒有它，檢討就只是寫完不會再看的日記。
     */
    @Test
    void questionsAsked_shouldComeFromPastInterviewReviews() throws Exception {
        mockMvc.perform(get("/api/question-types/asked").with(user(me)))
                .andExpect(status().isOk())
                // 還沒有任何檢討，回空陣列而不是報錯
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    private String createProject(String name, boolean isPublic) throws Exception {
        String body = mockMvc.perform(post("/api/projects").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","isPublic":%s}
                                """.formatted(name, isPublic)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private String createPreset(String label, Integer limit) throws Exception {
        String body = mockMvc.perform(post("/api/presets").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionTypeId":1,"label":"%s","targetCharLimit":%s}
                                """.formatted(label, limit == null ? "null" : limit)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private void addBlockToPreset(String presetId, UUID blockId) throws Exception {
        mockMvc.perform(post("/api/presets/{id}/blocks", presetId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockId":"%s"}
                                """.formatted(blockId)))
                .andExpect(status().isOk());
    }

    private static String extractId(String json) {
        return json.replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }
}
