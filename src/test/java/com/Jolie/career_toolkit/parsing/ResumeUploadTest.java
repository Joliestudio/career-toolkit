package com.Jolie.career_toolkit.parsing;

import com.Jolie.career_toolkit.IntegrationTestBase;
import com.Jolie.career_toolkit.block.BlockRepository;
import com.Jolie.career_toolkit.storage.FileStorage;
import com.Jolie.career_toolkit.user.AppUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
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
class ResumeUploadTest extends IntegrationTestBase {

    private static final String RESUME_TEXT = """
            Jolie Chen - Backend Engineer

            Skills: Java, Spring Boot, PostgreSQL, Docker, k8s
            Also familiar with React and TypeScript.

            Certifications: AWS SAA, PMP
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeFileRepository fileRepository;

    @Autowired
    private ExtractionCandidateRepository candidateRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private FileStorage storage;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;

    @BeforeEach
    void setUp() {
        me = createUser(uniqueEmail("uploader"));
    }

    // ---------- 上傳與型別驗證 ----------

    @Test
    void uploadPlainText_shouldParseAndExtractCandidates() throws Exception {
        String id = upload("resume.txt", ResumeFixtures.plainText(RESUME_TEXT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parseStatus").value("PARSED"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");

        mockMvc.perform(get("/api/resume-files/{id}/candidates", id).with(user(me)))
                .andExpect(status().isOk())
                // 別名要被解析成正規名稱：k8s → Kubernetes、AWS SAA → 完整證照名
                .andExpect(jsonPath("$[?(@.normalized == 'Kubernetes')]").exists())
                .andExpect(jsonPath("$[?(@.normalized == 'Spring Boot')]").exists())
                .andExpect(jsonPath("$[?(@.normalized == 'PMP')]").exists())
                // 每一筆都要標明是誰抽的，之後才比較得出字典 vs LLM
                .andExpect(jsonPath("$[0].extractor").value("DICTIONARY_V1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void uploadPdfWithText_shouldParse() throws Exception {
        upload("resume.pdf", ResumeFixtures.pdfWithText(RESUME_TEXT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
    }

    @Test
    void uploadDocx_shouldParse() throws Exception {
        upload("resume.docx", ResumeFixtures.docx(RESUME_TEXT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.contentType").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    /**
     * 這是 P5 最重要的行為。
     *
     * 掃描檔抽不出東西時，系統必須明確說「這是掃描檔」，
     * 而不是回一句「解析成功，找到 0 個技能」讓使用者一頭霧水地重試同一個檔案。
     */
    @Test
    void uploadScannedPdf_shouldReportNoTextLayerNotSilentEmptiness() throws Exception {
        upload("scanned.pdf", ResumeFixtures.scannedPdfWithoutTextLayer())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parseStatus").value("NO_TEXT_LAYER"))
                // 錯誤訊息要說得出「該怎麼辦」，不是只說「失敗」
                .andExpect(jsonPath("$.parseError").value(
                        org.hamcrest.Matchers.containsString("沒有文字層")));
    }

    /** 把執行檔改名成 .pdf —— 最基本的攻擊手法。型別必須用 magic byte 判斷。 */
    @Test
    void uploadExecutableRenamedAsPdf_shouldBeRejected() throws Exception {
        upload("totally-a-resume.pdf", ResumeFixtures.windowsExecutable())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Unsupported file type"))
                // 訊息帶出偵測到的真實類型，否則使用者會一直重試同一個檔案
                .andExpect(jsonPath("$.detected").exists());
    }

    // ---------- 儲存 ----------

    @Test
    void uploadedFile_shouldBeRetrievableFromStorage() throws Exception {
        byte[] content = ResumeFixtures.plainText(RESUME_TEXT);
        String id = uploadAndGetId("resume.txt", content);

        entityManager.flush();
        entityManager.clear();

        ResumeFile file = fileRepository.findById(UUID.fromString(id)).orElseThrow();

        assertThat(storage.exists(file.getStorageKey())).isTrue();
        // 儲存 key 是伺服器產生的，跟使用者的檔名完全無關（路徑穿越防護）
        assertThat(file.getStorageKey())
                .startsWith(me.id().toString() + "/")
                .doesNotContain("resume.txt");
    }

    @Test
    void uploadingTheSameFileTwice_shouldNotCreateDuplicates() throws Exception {
        byte[] content = ResumeFixtures.plainText(RESUME_TEXT);

        String first = uploadAndGetId("resume.txt", content);
        // 檔名不同但內容一樣 —— 靠 sha256 認得出來
        String second = uploadAndGetId("resume-copy.txt", content);

        assertThat(second).isEqualTo(first);

        mockMvc.perform(get("/api/resume-files").with(user(me)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- 候選審核 ----------

    @Test
    void acceptingCandidate_shouldCreateABlock() throws Exception {
        String fileId = uploadAndGetId("resume.txt", ResumeFixtures.plainText(RESUME_TEXT));
        String candidateId = firstCandidateId(fileId);

        mockMvc.perform(post("/api/resume-files/candidates/{id}/accept", candidateId)
                        .with(user(me)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.blockId").exists());

        entityManager.flush();
        entityManager.clear();

        assertThat(blockRepository.findByUserIdAndDeletedAtIsNull(me.id())).hasSize(1);
    }

    @Test
    void acceptingTwice_shouldNotCreateTwoBlocks() throws Exception {
        String fileId = uploadAndGetId("resume.txt", ResumeFixtures.plainText(RESUME_TEXT));
        String candidateId = firstCandidateId(fileId);

        accept(candidateId);
        accept(candidateId);

        entityManager.flush();
        entityManager.clear();
        assertThat(blockRepository.findByUserIdAndDeletedAtIsNull(me.id())).hasSize(1);
    }

    /**
     * 被拒絕的候選不能因為重新上傳同一份檔案就復活。
     * 沒有這個保證的話，使用者每次重新解析都要再拒絕一次同樣的東西。
     */
    @Test
    void rejectedCandidate_shouldStayRejected() throws Exception {
        byte[] content = ResumeFixtures.plainText(RESUME_TEXT);
        String fileId = uploadAndGetId("resume.txt", content);
        String candidateId = firstCandidateId(fileId);

        mockMvc.perform(post("/api/resume-files/candidates/{id}/reject", candidateId)
                        .with(user(me)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        entityManager.flush();

        // 重新上傳同一份檔案（sha256 相同 → 回既有那筆）
        uploadAndGetId("resume-again.txt", content);
        entityManager.flush();
        entityManager.clear();

        ExtractionCandidate stillRejected = candidateRepository
                .findById(UUID.fromString(candidateId)).orElseThrow();
        assertThat(stillRejected.getStatus()).isEqualTo(ExtractionCandidate.Status.REJECTED);
    }

    /**
     * 產生的積木內容留空給使用者自己寫。
     *
     * 解析只能告訴你「你會 Kubernetes」，沒辦法幫你寫出「我用 Kubernetes 做了什麼」。
     * 那句話只有本人寫得出來，而那句話才是履歷上真正有價值的東西。
     */
    @Test
    void acceptedBlock_shouldUseTheCanonicalNameAsTitle() throws Exception {
        String fileId = uploadAndGetId("resume.txt", ResumeFixtures.plainText("Skills: k8s"));
        String candidateId = firstCandidateId(fileId);

        accept(candidateId);
        entityManager.flush();
        entityManager.clear();

        // 標題是正規名稱 Kubernetes，不是原文的 k8s
        assertThat(blockRepository.findByUserIdAndDeletedAtIsNull(me.id()))
                .extracting(b -> b.getTitle())
                .contains("Kubernetes");
    }

    // ---------- 跨帳號隔離 ----------

    @Test
    void anotherUser_shouldGet404() throws Exception {
        String fileId = uploadAndGetId("resume.txt", ResumeFixtures.plainText(RESUME_TEXT));
        String candidateId = firstCandidateId(fileId);
        AppUserDetails other = createUser(uniqueEmail("intruder"));

        mockMvc.perform(get("/api/resume-files/{id}", fileId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resume-files/{id}/candidates", fileId).with(user(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/resume-files/candidates/{id}/accept", candidateId)
                        .with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/resume-files").with(user(other)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anonymous_shouldGet401() throws Exception {
        mockMvc.perform(get("/api/resume-files"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions upload(String name, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart("/api/resume-files")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .with(user(me)).with(csrf()));
    }

    private String uploadAndGetId(String name, byte[] content) throws Exception {
        return upload(name, content)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }

    private String firstCandidateId(String fileId) throws Exception {
        String body = mockMvc.perform(get("/api/resume-files/{id}/candidates", fileId).with(user(me)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return body.replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }

    private void accept(String candidateId) throws Exception {
        mockMvc.perform(post("/api/resume-files/candidates/{id}/accept", candidateId)
                        .with(user(me)).with(csrf()))
                .andExpect(status().isOk());
    }
}
