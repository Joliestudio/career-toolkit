package com.Jolie.career_toolkit.block;

import com.Jolie.career_toolkit.IntegrationTestBase;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 打真正的 HTTP 端點 + 真正的 PostgreSQL，不 mock Service。
 *
 * 這裡刻意不用 @WebMvcTest + mock：五個端點的驗收標準有一半是關於資料庫真實狀態的
 * （軟刪除之後那筆到底還在不在），mock 掉 Service 就什麼也證明不了。
 *
 * 注意：@MockBean 在 Spring Boot 4 已經被移除，要 mock 的話是
 * org.springframework.test.context.bean.override.mockito.@MockitoBean。
 */
@AutoConfigureMockMvc
@Transactional
class BlockControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockRepository blockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;

    @BeforeEach
    void setUp() {
        me = createUser(uniqueEmail("owner"));
    }

    @Test
    void createBlock_shouldReturn201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/api/blocks").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"Java","content":"Spring Boot 開發"}
                                """))
                .andExpect(status().isCreated())
                // 201 必須帶 Location 指向新資源，客戶端才不用自己拼 URL
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        ".*/api/blocks/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.title").value("Java"))
                // "Spring Boot 開發" = 11 個 ASCII + 1 個空格 + 2 個中文 = 14
                .andExpect(jsonPath("$.charCount").value(14));
    }

    @Test
    void createBlock_shouldReturn400WhenTitleExceeds200Chars() throws Exception {
        String tooLong = "a".repeat(201);

        mockMvc.perform(post("/api/blocks").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"%s","content":"內容"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBlock_shouldReturn400WhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/api/blocks").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"","content":"內容"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBlocks_shouldFilterByType() throws Exception {
        blockRepository.save(new Block(me.id(), BlockType.SKILL, "技能", "Java"));
        blockRepository.save(new Block(me.id(), BlockType.EXPERIENCE, "經歷", "後端工程師"));
        entityManager.flush();

        mockMvc.perform(get("/api/blocks").with(user(me)).param("type", "SKILL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SKILL"));

        // 不帶 type 時要拿到全部
        mockMvc.perform(get("/api/blocks").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deleteBlock_shouldSoftDeleteAndKeepRowInDatabase() throws Exception {
        Block block = blockRepository.save(new Block(me.id(), BlockType.SKILL, "要刪的", "內容"));
        UUID id = block.getId();
        entityManager.flush();

        mockMvc.perform(delete("/api/blocks/{id}", id).with(user(me)).with(csrf()))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        // 列表撈不到
        mockMvc.perform(get("/api/blocks").with(user(me)))
                .andExpect(jsonPath("$.length()").value(0));

        // 但資料庫那筆還在，只是 deleted_at 有值——這才是軟刪除
        Block stillThere = blockRepository.findById(id).orElseThrow();
        assertThat(stillThere.getDeletedAt()).isNotNull();
    }

    @Test
    void updateBlock_shouldNotClearContentWhenOnlyTitleIsGiven() throws Exception {
        Block block = blockRepository.save(new Block(me.id(), BlockType.SKILL, "原標題", "原內容"));
        UUID id = block.getId();
        entityManager.flush();

        // PATCH 只給 title，沒給 content
        mockMvc.perform(patch("/api/blocks/{id}", id).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"新標題"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("新標題"))
                // PATCH 是部分更新：沒給的欄位代表「不要動」，不是「清空」。
                // 寫成無條件覆寫的話，使用者只改標題就會把內容洗掉。
                .andExpect(jsonPath("$.content").value("原內容"));

        entityManager.flush();
        entityManager.clear();

        Block reloaded = blockRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("新標題");
        assertThat(reloaded.getContent()).isEqualTo("原內容");
    }

    @Test
    void updateBlock_shouldRecalculateCharCount() throws Exception {
        Block block = blockRepository.save(new Block(me.id(), BlockType.SKILL, "標題", "abc"));
        entityManager.flush();

        mockMvc.perform(patch("/api/blocks/{id}", block.getId()).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"後端工程師🚀"}
                                """))
                .andExpect(status().isOk())
                // 字數必須跟著內容連動更新，而且 emoji 算一個字
                .andExpect(jsonPath("$.charCount").value(6));
    }

    @Test
    void getBlock_shouldReturnTheBlock() throws Exception {
        Block block = blockRepository.save(new Block(me.id(), BlockType.PROJECT, "專案", "內容"));
        entityManager.flush();

        mockMvc.perform(get("/api/blocks/{id}", block.getId()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(block.getId().toString()))
                .andExpect(jsonPath("$.type").value("PROJECT"));
    }

    @Test
    void createBlock_shouldDefaultTagsToEmptyList() throws Exception {
        mockMvc.perform(post("/api/blocks").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CERTIFICATION","title":"AWS SAA","content":"2025 取得"}
                                """))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        List<Block> blocks = blockRepository.findByUserIdAndDeletedAtIsNull(me.id());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getTags()).isEmpty();
    }

    // ---------- 未登入 ----------

    @Test
    void anonymousRequest_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isUnauthorized())
                // 預設行為是導向登入頁（302）。對 JSON API 來說那是錯的，
                // 前端的 fetch 會拿到一頁 HTML 而判斷不出「要重新登入」。
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------- 跨帳號隔離：這一組是 P1 存在的理由 ----------

    @Test
    void singleBlock_ofAnotherUser_shouldReturn404Not403() throws Exception {
        AppUserDetails other = createUser(uniqueEmail("other"));
        Block hers = blockRepository.save(new Block(me.id(), BlockType.SKILL, "我的秘密", "內容"));
        entityManager.flush();

        // 回 404 而不是 403：403 等於確認「這個 id 真的存在」，
        // 可以拿來逐一探測哪些 id 有效。404 什麼也沒說。
        mockMvc.perform(get("/api/blocks/{id}", hers.getId()).with(user(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/blocks/{id}", hers.getId()).with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"被別人改掉"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/blocks/{id}", hers.getId()).with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());

        // 確認真的沒被改到
        entityManager.flush();
        entityManager.clear();
        Block untouched = blockRepository.findById(hers.getId()).orElseThrow();
        assertThat(untouched.getTitle()).isEqualTo("我的秘密");
        assertThat(untouched.getDeletedAt()).isNull();
    }

    /**
     * 負向列表測試。
     *
     * 單筆的測試抓不到「list 端點忘了加 WHERE user_id」這個 bug——
     * 因為單筆查詢有自己的條件。必須有一個測試去數列表的長度。
     */
    @Test
    void list_shouldOnlyContainOwnBlocks() throws Exception {
        AppUserDetails other = createUser(uniqueEmail("other"));

        blockRepository.save(new Block(me.id(), BlockType.SKILL, "我的 1", "x"));
        blockRepository.save(new Block(me.id(), BlockType.SKILL, "我的 2", "x"));
        blockRepository.save(new Block(me.id(), BlockType.SKILL, "我的 3", "x"));
        blockRepository.save(new Block(other.id(), BlockType.SKILL, "別人的 1", "x"));
        blockRepository.save(new Block(other.id(), BlockType.SKILL, "別人的 2", "x"));
        entityManager.flush();

        mockMvc.perform(get("/api/blocks").with(user(me)))
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/blocks").with(user(other)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createdBlock_shouldBelongToTheLoggedInUserNotAnyoneElse() throws Exception {
        mockMvc.perform(post("/api/blocks").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"新的","content":"內容"}
                                """))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        // user_id 只能來自 SecurityContext，絕不能從 request body 拿
        List<Block> mine = blockRepository.findByUserIdAndDeletedAtIsNull(me.id());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getUserId()).isEqualTo(me.id());
    }
}
