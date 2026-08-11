package com.Jolie.career_toolkit.selection;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class BlockSelectionTest extends IntegrationTestBase {

    private static final String BACKEND_JD = """
            後端工程師（Java）

            工作內容：
            - 使用 Spring Boot 開發 RESTful API
            - PostgreSQL 資料庫設計與最佳化
            - Docker / Kubernetes 部署與維運

            必備條件：
            - 三年以上 Java 後端開發經驗
            - 熟悉 Spring Boot 生態系
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private KeywordBlockSelector selector;

    @Autowired
    private LlmClient llmClient;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;

    @BeforeEach
    void setUp() {
        me = createUser(uniqueEmail("selector"));
    }

    // ---------- 這一組是 P7 的驗收核心 ----------

    /**
     * 整套測試必須在「完全沒有網路、沒有 API key」的情況下通過。
     *
     * 這不是為了測試方便——它是一個產品保證：沒有 key 的人也拿得到一個會動的功能，
     * LLM 是升級選項不是前提。
     */
    @Test
    void selectionShouldWorkWithNoLlmConfigured() {
        assertThat(llmClient.isAvailable()).isFalse();
        assertThat(selector.name()).isEqualTo("KEYWORD_V1");
    }

    @Test
    void shouldRankRelevantBlocksHigherAndExplainWhy() throws Exception {
        blockRepository.save(new Block(me.id(), BlockType.SKILL,
                "Spring Boot 後端開發", "用 Spring Boot 與 PostgreSQL 開發 API"));
        blockRepository.save(new Block(me.id(), BlockType.SKILL,
                "平面設計", "熟悉 Photoshop 與 Illustrator"));
        blockRepository.save(new Block(me.id(), BlockType.EXPERIENCE,
                "維運工程師", "負責 Docker 與 Kubernetes 叢集維護"));
        entityManager.flush();

        mockMvc.perform(post("/api/selection").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"%s"}
                                """.formatted(BACKEND_JD.replace("\n", "\\n"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selector").value("KEYWORD_V1"))
                // 最相關的排第一
                .andExpect(jsonPath("$.items[0].title").value("Spring Boot 後端開發"))
                // 理由是必要的不是裝飾——只給排名，使用者沒辦法判斷推薦有沒有道理
                .andExpect(jsonPath("$.items[0].reasons").isNotEmpty())
                // 完全不相關的不該出現
                .andExpect(jsonPath("$.items[?(@.title == '平面設計')]").doesNotExist());
    }

    @Test
    void reasonsShouldNameTheActualMatchedTerms() throws Exception {
        blockRepository.save(new Block(me.id(), BlockType.SKILL,
                "Kubernetes 叢集維運", "用 k8s 管理服務"));
        entityManager.flush();

        String body = mockMvc.perform(post("/api/selection").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"需要 Kubernetes 與 Docker 經驗"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Kubernetes");
    }

    /** 積木庫是空的不該報錯，只是沒有東西可選。 */
    @Test
    void emptyBlockLibraryShouldReturnEmptyNotError() throws Exception {
        mockMvc.perform(post("/api/selection").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"任何職缺描述"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.candidateCount").value(0));
    }

    @Test
    void blankJobDescriptionShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/selection").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.jobDescription").exists());
    }

    // ---------- 快取 ----------

    @Test
    void sameJobDescriptionTwiceShouldHitCache() throws Exception {
        blockRepository.save(new Block(me.id(), BlockType.SKILL, "Java", "後端"));
        entityManager.flush();

        select("需要 Java 經驗").andExpect(jsonPath("$.cached").value(false));
        entityManager.flush();

        // 同樣的 JD + 同樣的積木 → 結果一定相同，不需要重算。
        // 現在只省下幾毫秒，接上 LLM 之後省下的是真的錢。
        select("需要 Java 經驗").andExpect(jsonPath("$.cached").value(true));
    }

    /**
     * 積木改了之後不能再回舊的快取。
     *
     * hash 帶上 updatedAt 就是為了這件事——少了它，改完積木再貼同一份 JD
     * 會拿到過期的推薦，而且完全看不出來。
     */
    @Test
    void editingABlockShouldInvalidateTheCache() throws Exception {
        Block block = blockRepository.save(new Block(me.id(), BlockType.SKILL, "Java", "後端"));
        entityManager.flush();

        select("需要 Java 經驗").andExpect(jsonPath("$.cached").value(false));
        entityManager.flush();

        block.updateContent("後端，另外也做前端");
        entityManager.flush();
        entityManager.clear();

        select("需要 Java 經驗").andExpect(jsonPath("$.cached").value(false));
    }

    // ---------- 隔離 ----------

    @Test
    void shouldOnlyConsiderOwnBlocks() throws Exception {
        AppUserDetails other = createUser(uniqueEmail("other"));
        blockRepository.save(new Block(other.id(), BlockType.SKILL,
                "Spring Boot 大神", "別人的積木"));
        entityManager.flush();

        mockMvc.perform(post("/api/selection").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"需要 Spring Boot 經驗"}
                                """))
                .andExpect(status().isOk())
                // 別人的積木一個都不該進入候選
                .andExpect(jsonPath("$.candidateCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void anonymousShouldGet401() throws Exception {
        mockMvc.perform(post("/api/selection").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"任何內容"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 選擇器單元行為 ----------

    @Test
    void titleMatchShouldOutrankContentMatch() {
        Block titled = new Block(me.id(), BlockType.SKILL, "Kubernetes", "一些內容");
        Block mentioned = new Block(me.id(), BlockType.SKILL, "雜項", "順帶提到 Kubernetes");

        List<BlockSelector.Selection> result =
                selector.select("需要 Kubernetes", List.of(titled, mentioned));

        // 標題是你自己下的摘要，命中標題代表這塊積木「整體」就在講那件事；
        // 內文命中可能只是順帶提了一句
        assertThat(result.get(0).blockId()).isEqualTo(titled.getId());
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    @Test
    void selectorShouldNotWriteAnyBlockContent() {
        Block block = new Block(me.id(), BlockType.SKILL, "Java", "原本的內容");

        List<BlockSelector.Selection> result = selector.select("Java 後端", List.of(block));

        // 產品立場：選擇器負責「挑選與排序」，不負責「寫」積木。
        // 回傳的只有 id、分數、理由——不可能改到你的文字。
        assertThat(result).hasSize(1);
        assertThat(block.getContent()).isEqualTo("原本的內容");
    }

    private org.springframework.test.web.servlet.ResultActions select(String jd) throws Exception {
        return mockMvc.perform(post("/api/selection").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"%s"}
                                """.formatted(jd)))
                .andExpect(status().isOk());
    }
}
