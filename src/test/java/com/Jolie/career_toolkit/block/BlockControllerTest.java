package com.Jolie.career_toolkit.block;

import com.Jolie.career_toolkit.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4 把測試自動設定按模組拆開了：
//   org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc  (Boot 3)
//   → org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc      (Boot 4)
// 這跟依賴從 spring-boot-starter-test 拆成 spring-boot-starter-webmvc-test 是同一件事。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockRepository blockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void createBlock_shouldReturn201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/api/blocks")
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

        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"%s","content":"內容"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBlock_shouldReturn400WhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"","content":"內容"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBlocks_shouldFilterByType() throws Exception {
        blockRepository.save(new Block(DEV_USER_ID, BlockType.SKILL, "技能", "Java"));
        blockRepository.save(new Block(DEV_USER_ID, BlockType.EXPERIENCE, "經歷", "後端工程師"));
        entityManager.flush();

        mockMvc.perform(get("/api/blocks").param("type", "SKILL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SKILL"));

        // 不帶 type 時要拿到全部
        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deleteBlock_shouldSoftDeleteAndKeepRowInDatabase() throws Exception {
        Block block = blockRepository.save(
                new Block(DEV_USER_ID, BlockType.SKILL, "要刪的", "內容"));
        UUID id = block.getId();
        entityManager.flush();

        mockMvc.perform(delete("/api/blocks/{id}", id))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        // 列表撈不到
        mockMvc.perform(get("/api/blocks"))
                .andExpect(jsonPath("$.length()").value(0));

        // 但資料庫那筆還在，只是 deleted_at 有值——這才是軟刪除
        Block stillThere = blockRepository.findById(id).orElseThrow();
        assertThat(stillThere.getDeletedAt()).isNotNull();
    }

    @Test
    void updateBlock_shouldNotClearContentWhenOnlyTitleIsGiven() throws Exception {
        Block block = blockRepository.save(
                new Block(DEV_USER_ID, BlockType.SKILL, "原標題", "原內容"));
        UUID id = block.getId();
        entityManager.flush();

        // PATCH 只給 title，沒給 content
        mockMvc.perform(patch("/api/blocks/{id}", id)
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
        Block block = blockRepository.save(
                new Block(DEV_USER_ID, BlockType.SKILL, "標題", "abc"));
        UUID id = block.getId();
        entityManager.flush();

        mockMvc.perform(patch("/api/blocks/{id}", id)
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
        Block block = blockRepository.save(
                new Block(DEV_USER_ID, BlockType.PROJECT, "專案", "內容"));
        entityManager.flush();

        mockMvc.perform(get("/api/blocks/{id}", block.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(block.getId().toString()))
                .andExpect(jsonPath("$.type").value("PROJECT"));
    }

    @Test
    void getBlocks_shouldNotReturnOtherUsersBlocks() throws Exception {
        UUID otherUser = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        blockRepository.save(new Block(DEV_USER_ID, BlockType.SKILL, "我的", "內容"));
        entityManager.flush();

        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("我的"));

        assertThat(blockRepository.findByUserIdAndDeletedAtIsNull(otherUser)).isEmpty();
    }

    @Test
    void createBlock_shouldDefaultTagsToEmptyList() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CERTIFICATION","title":"AWS SAA","content":"2025 取得"}
                                """))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        List<Block> blocks = blockRepository.findByUserIdAndDeletedAtIsNull(DEV_USER_ID);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getTags()).isEmpty();
    }
}
