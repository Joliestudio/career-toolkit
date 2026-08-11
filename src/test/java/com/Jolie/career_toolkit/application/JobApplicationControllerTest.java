package com.Jolie.career_toolkit.application;

import com.Jolie.career_toolkit.IntegrationTestBase;
import com.Jolie.career_toolkit.company.Company;
import com.Jolie.career_toolkit.company.CompanyRepository;
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
class JobApplicationControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        me = createUser(uniqueEmail("applicant"));
        companyId = companyRepository.save(
                new Company("台積電-" + UUID.randomUUID(), null, me.id())).getId();
        entityManager.flush();
    }

    // ---------- 建立 ----------

    @Test
    void create_shouldReturn201AndWriteFirstHistoryEntry() throws Exception {
        String id = createApplication("後端工程師");

        mockMvc.perform(get("/api/applications/{id}/history", id).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                // 第一筆的 fromStatus 是 null：從「不存在」到初始狀態
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].toStatus").value("APPLIED"));
    }

    @Test
    void create_shouldExposeAllowedNextStates() throws Exception {
        mockMvc.perform(post("/api/applications").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","positionTitle":"後端工程師"}
                                """.formatted(companyId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                // 前端的下拉選單直接用這個，就不用把狀態機在前端再實作一次
                .andExpect(jsonPath("$.allowedNextStates").isArray())
                .andExpect(jsonPath("$.allowedNextStates[?(@ == 'SCREENING')]").exists())
                .andExpect(jsonPath("$.allowedNextStates[?(@ == 'GHOSTED')]").exists());
    }

    @Test
    void create_asDraft_shouldNotSetAppliedAt() throws Exception {
        mockMvc.perform(post("/api/applications").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","positionTitle":"還沒投","status":"DRAFT"}
                                """.formatted(companyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                // DRAFT 代表還沒真的投出去，投遞日期就不該有值
                .andExpect(jsonPath("$.appliedAt").doesNotExist());
    }

    // ---------- 狀態機 ----------

    @Test
    void illegalTransition_shouldReturn400AndNameTheLegalAlternatives() throws Exception {
        String id = createApplication("後端工程師");

        // APPLIED 不能直接跳到 ACCEPTED
        mockMvc.perform(post("/api/applications/{id}/status", id).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACCEPTED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Illegal status transition"))
                .andExpect(jsonPath("$.from").value("APPLIED"))
                .andExpect(jsonPath("$.to").value("ACCEPTED"))
                // 錯誤訊息一定要說得出「那我可以改成什麼」
                .andExpect(jsonPath("$.allowed[?(@ == 'SCREENING')]").exists())
                .andExpect(jsonPath("$.allowed[?(@ == 'INTERVIEWING')]").exists());
    }

    @Test
    void terminalStatus_shouldRejectAnyFurtherTransition() throws Exception {
        String id = createApplication("後端工程師");
        changeStatus(id, "REJECTED").andExpect(status().isOk());

        mockMvc.perform(post("/api/applications/{id}/status", id).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INTERVIEWING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").isEmpty());
    }

    @Test
    void threeStatusChanges_shouldProduceFourHistoryEntries() throws Exception {
        String id = createApplication("後端工程師");

        changeStatus(id, "SCREENING").andExpect(status().isOk());
        changeStatus(id, "INTERVIEWING").andExpect(status().isOk());
        changeStatus(id, "OFFER").andExpect(status().isOk());

        // 建立時 1 筆 + 三次轉換 3 筆 = 4 筆。
        // 這種資料無法事後補，所以要從第一天就寫。
        mockMvc.perform(get("/api/applications/{id}/history", id).with(user(me)))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].toStatus").value("SCREENING"))
                .andExpect(jsonPath("$[3].toStatus").value("OFFER"));
    }

    @Test
    void changingToTheSameStatus_shouldNotWriteHistory() throws Exception {
        String id = createApplication("後端工程師");

        changeStatus(id, "APPLIED").andExpect(status().isOk());
        changeStatus(id, "APPLIED").andExpect(status().isOk());

        // 沒有變化就不寫，否則歷程會被同一個狀態洗版，看不出真正的轉折點
        mockMvc.perform(get("/api/applications/{id}/history", id).with(user(me)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void ghostedApplication_canComeBackToLife() throws Exception {
        String id = createApplication("後端工程師");

        changeStatus(id, "GHOSTED").andExpect(status().isOk());
        // 對方隔了三週突然回信——這是真的會發生的事
        changeStatus(id, "INTERVIEWING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"));
    }

    @Test
    void statusNote_shouldBeStoredInHistory() throws Exception {
        String id = createApplication("後端工程師");

        mockMvc.perform(post("/api/applications/{id}/status", id).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED","note":"HR 說找到更資深的人選"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/applications/{id}/history", id).with(user(me)))
                .andExpect(jsonPath("$[1].note").value("HR 說找到更資深的人選"));
    }

    // ---------- 部分更新 ----------

    @Test
    void patch_shouldNotClearFieldsThatWereNotSupplied() throws Exception {
        String id = createApplication("後端工程師");

        mockMvc.perform(patch("/api/applications/{id}", id).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"透過內推"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("透過內推"))
                // 沒給的欄位代表「不要動」，不是「清空」
                .andExpect(jsonPath("$.positionTitle").value("後端工程師"));
    }

    // ---------- 與履歷版本的關聯 ----------

    @Test
    void canLinkOwnResumeVersion() throws Exception {
        String applicationId = createApplication("後端工程師");
        String versionId = createResumeVersion(me, "投這家用的版本");

        mockMvc.perform(patch("/api/applications/{id}", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeVersionId":"%s"}
                                """.formatted(versionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeVersionId").value(versionId));
    }

    /**
     * 掛別人的履歷版本要被擋下來。
     *
     * 這裡有兩道防線：Service 先查「這份履歷是不是你的」（回 404），
     * 資料庫還有一個複合外鍵 (user_id, resume_version_id)。
     * 就算 Service 那道被誰刪掉，資料庫仍然擋得住——
     * 單純的 REFERENCES resume_versions(id) 做不到這件事，
     * 那只保證「那個版本存在」，不保證「是你的」。
     */
    @Test
    void cannotLinkAnotherUsersResumeVersion() throws Exception {
        String applicationId = createApplication("後端工程師");
        AppUserDetails other = createUser(uniqueEmail("other"));
        String theirVersion = createResumeVersion(other, "別人的履歷");

        mockMvc.perform(patch("/api/applications/{id}", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeVersionId":"%s"}
                                """.formatted(theirVersion)))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        assertThat(applicationRepository.findById(UUID.fromString(applicationId)).orElseThrow()
                .getResumeVersionId()).isNull();
    }

    // ---------- 跨帳號隔離 ----------

    @Test
    void anotherUser_shouldGet404OnEveryOperation() throws Exception {
        String id = createApplication("我的投遞");
        AppUserDetails other = createUser(uniqueEmail("intruder"));

        mockMvc.perform(get("/api/applications/{id}", id).with(user(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/applications/{id}/history", id).with(user(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/applications/{id}", id).with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionTitle":"被改掉了"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/applications/{id}/status", id).with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/applications/{id}", id).with(user(other)).with(csrf()))
                .andExpect(status().isNotFound());

        // 確認真的沒被動到
        entityManager.flush();
        entityManager.clear();
        JobApplication untouched = applicationRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(untouched.getPositionTitle()).isEqualTo("我的投遞");
        assertThat(untouched.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(untouched.getDeletedAt()).isNull();
    }

    /** 單筆的測試抓不到「list 端點忘了加 WHERE user_id」——必須有一個測試去數列表長度。 */
    @Test
    void list_shouldOnlyContainOwnApplications() throws Exception {
        createApplication("我的 1");
        createApplication("我的 2");

        AppUserDetails other = createUser(uniqueEmail("other"));
        mockMvc.perform(post("/api/applications").with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","positionTitle":"別人的"}
                                """.formatted(companyId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications").with(user(me)))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/applications").with(user(other)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].positionTitle").value("別人的"));
    }

    @Test
    void anonymous_shouldGet401() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 篩選與軟刪除 ----------

    @Test
    void list_shouldFilterByStatus() throws Exception {
        String rejected = createApplication("被拒的");
        createApplication("還活著的");
        changeStatus(rejected, "REJECTED").andExpect(status().isOk());

        mockMvc.perform(get("/api/applications").with(user(me)).param("status", "REJECTED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].positionTitle").value("被拒的"));
    }

    @Test
    void delete_shouldSoftDeleteAndKeepRowInDatabase() throws Exception {
        String id = createApplication("要刪的");

        mockMvc.perform(delete("/api/applications/{id}", id).with(user(me)).with(csrf()))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/applications").with(user(me)))
                .andExpect(jsonPath("$.length()").value(0));

        JobApplication stillThere = applicationRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stillThere.getDeletedAt()).isNotNull();
    }

    // ---------- helpers ----------

    private String createApplication(String title) throws Exception {
        String body = mockMvc.perform(post("/api/applications").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","positionTitle":"%s"}
                                """.formatted(companyId, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return body.replaceAll(".*\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }

    private String createResumeVersion(AppUserDetails owner, String label) throws Exception {
        String body = mockMvc.perform(post("/api/resumes").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"%s"}
                                """.formatted(label)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return body.replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(String id, String status)
            throws Exception {
        return mockMvc.perform(post("/api/applications/{id}/status", id).with(user(me)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"%s"}
                        """.formatted(status)));
    }
}
