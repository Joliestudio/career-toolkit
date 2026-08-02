package com.Jolie.career_toolkit.interview;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class InterviewAndOfferTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AppUserDetails me;
    private UUID companyId;
    private String applicationId;

    @BeforeEach
    void setUp() throws Exception {
        me = createUser(uniqueEmail("candidate"));
        companyId = companyRepository.save(
                new Company("聯發科-" + UUID.randomUUID(), null, me.id())).getId();
        entityManager.flush();
        applicationId = createApplication();
    }

    // ---------- 面試 ----------

    @Test
    void createInterview_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/applications/{id}/interviews", applicationId)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"round":1,"stage":"HR_SCREEN","format":"VIDEO",
                                 "scheduledAt":"%s","durationMinutes":30}
                                """.formatted(inDays(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.stage").value("HR_SCREEN"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    /**
     * 「我未來有幾間面試」——這是使用者列在第一位的需求。
     *
     * 三個條件都要成立：只回 SCHEDULED、只回現在之後、依時間排序。
     * 少任何一個，這個清單就不能當待辦用。
     */
    @Test
    void upcoming_shouldOnlyReturnFutureScheduledInterviewsInOrder() throws Exception {
        createInterview(1, inDays(-2));    // 已經過去
        createInterview(2, inDays(5));
        createInterview(3, inDays(1));
        String cancelledId = createInterview(4, inDays(2));

        mockMvc.perform(patch("/api/interviews/{id}", cancelledId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"round":4,"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk());

        entityManager.flush();

        mockMvc.perform(get("/api/interviews/upcoming").with(user(me)).param("days", "14"))
                .andExpect(status().isOk())
                // 過去的那場、被取消的那場都不該出現
                .andExpect(jsonPath("$.length()").value(2))
                // 依時間排序：第 1 天的在第 5 天的前面
                .andExpect(jsonPath("$[0].round").value(3))
                .andExpect(jsonPath("$[1].round").value(2));
    }

    @Test
    void upcoming_shouldRespectTheDaysWindow() throws Exception {
        createInterview(1, inDays(3));
        createInterview(2, inDays(20));

        mockMvc.perform(get("/api/interviews/upcoming").with(user(me)).param("days", "7"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].round").value(1));
    }

    @Test
    void upcoming_shouldNotLeakOtherUsersInterviews() throws Exception {
        createInterview(1, inDays(3));
        AppUserDetails other = createUser(uniqueEmail("stranger"));

        // 這是唯一需要 join 回 applications 才能確認所有權的查詢，
        // 少了 a.userId = :userId 就會把所有人的面試都撈出來
        mockMvc.perform(get("/api/interviews/upcoming").with(user(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void interviewOfAnotherUser_shouldReturn404() throws Exception {
        String interviewId = createInterview(1, inDays(3));
        AppUserDetails other = createUser(uniqueEmail("stranger"));

        mockMvc.perform(patch("/api/interviews/{id}", interviewId).with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"round":1,"location":"被改掉了"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/interviews/{id}/review", interviewId).with(user(other)))
                .andExpect(status().isNotFound());
    }

    // ---------- 面試檢討 ----------

    @Test
    void review_shouldBeUpsertNotDuplicate() throws Exception {
        String interviewId = createInterview(1, inDays(-1));

        saveReview(interviewId, "第一版流程", (short) 3, (short) 4).andExpect(status().isOk());
        saveReview(interviewId, "改寫過的流程", (short) 5, (short) 2).andExpect(status().isOk());

        entityManager.flush();

        // 一場面試只會有一份檢討（DB 有 UNIQUE(interview_id)），
        // 使用者的心智模型是「編輯這場的檢討」，不是「新增第二份」
        mockMvc.perform(get("/api/interviews/{id}/review", interviewId).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processNotes").value("改寫過的流程"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.interestLevel").value(2));

        mockMvc.perform(get("/api/applications/{id}/reviews", applicationId).with(user(me)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * rating 和 interestLevel 必須能各自獨立。
     * 面得很順但公司文化有疑慮（高 rating、低 interest）是很常見的組合，
     * 而那正是收到 offer 時最需要看到的資訊。
     */
    @Test
    void ratingAndInterestLevel_areIndependent() throws Exception {
        String interviewId = createInterview(1, inDays(-1));

        saveReview(interviewId, "面得很順但文化有疑慮", (short) 5, (short) 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.interestLevel").value(1));
    }

    @Test
    void review_shouldRejectOutOfRangeRating() throws Exception {
        String interviewId = createInterview(1, inDays(-1));

        mockMvc.perform(put("/api/interviews/{id}/review", interviewId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":9}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
    }

    @Test
    void reviewsForApplication_shouldBeOrderedByRound() throws Exception {
        String first = createInterview(1, inDays(-5));
        String second = createInterview(2, inDays(-2));

        saveReview(second, "第二關", (short) 4, (short) 4).andExpect(status().isOk());
        saveReview(first, "第一關", (short) 3, (short) 5).andExpect(status().isOk());
        entityManager.flush();

        // 收到 offer 要做決策時，就是靠這個把每一輪的印象依序並排看
        mockMvc.perform(get("/api/applications/{id}/reviews", applicationId).with(user(me)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].processNotes").value("第一關"))
                .andExpect(jsonPath("$[1].processNotes").value("第二關"));
    }

    // ---------- Offer ----------

    /**
     * 台灣的 offer 沒有保證年薪根本沒辦法比較：
     * 月薪 50k × 14 個月 = 70 萬，月薪 55k × 12 個月 = 66 萬。
     * 月薪比較低的那個反而總額比較高——這正是這個欄位存在的理由。
     */
    @Test
    void annualisedTotal_shouldMultiplyMonthlySalaryByGuaranteedMonths() throws Exception {
        mockMvc.perform(put("/api/applications/{id}/offer", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":50000,"salaryPeriod":"MONTHLY","guaranteedMonths":14}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualisedTotal").value(700000.00));
    }

    @Test
    void annualisedTotal_shouldDefaultTo12MonthsWhenNotSpecified() throws Exception {
        mockMvc.perform(put("/api/applications/{id}/offer", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":55000,"salaryPeriod":"MONTHLY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualisedTotal").value(660000.00));
    }

    @Test
    void annualSalary_shouldNotBeMultiplied() throws Exception {
        mockMvc.perform(put("/api/applications/{id}/offer", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":900000,"salaryPeriod":"ANNUAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualisedTotal").value(900000.00));
    }

    @Test
    void deadlineCountdown_shouldReportDaysRemaining() throws Exception {
        mockMvc.perform(put("/api/applications/{id}/offer", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":60000,"replyDeadline":"%s"}
                                """.formatted(LocalDate.now().plusDays(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysUntilDeadline").value(3));
    }

    @Test
    void expiredDeadline_shouldReportNegativeDays() throws Exception {
        mockMvc.perform(put("/api/applications/{id}/offer", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":60000,"replyDeadline":"%s"}
                                """.formatted(LocalDate.now().minusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysUntilDeadline").value(-2));
    }

    /** 建立 offer 要把投遞狀態一起推進，否則兩邊會各說各話，漏斗統計跟著失真。 */
    @Test
    void creatingOffer_shouldAdvanceApplicationStatus() throws Exception {
        mockMvc.perform(put("/api/applications/{id}/offer", applicationId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":60000}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/applications/{id}", applicationId).with(user(me)))
                .andExpect(jsonPath("$.status").value("OFFER"));
    }

    @Test
    void acceptingOffer_shouldMarkApplicationAccepted() throws Exception {
        String offerId = createOffer();

        mockMvc.perform(post("/api/offers/{id}/decision", offerId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACCEPTED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.decidedAt").exists());

        mockMvc.perform(get("/api/applications/{id}", applicationId).with(user(me)))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    /** 婉拒也要記錄，而且理由尤其重要——三個月後回頭看只剩模糊印象。 */
    @Test
    void decliningOffer_shouldStoreTheReason() throws Exception {
        String offerId = createOffer();

        mockMvc.perform(post("/api/offers/{id}/decision", offerId).with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"DECLINED","declineReason":"通勤時間單程 90 分鐘"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("通勤時間單程 90 分鐘"));

        mockMvc.perform(get("/api/applications/{id}", applicationId).with(user(me)))
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void offerOfAnotherUser_shouldReturn404() throws Exception {
        String offerId = createOffer();
        AppUserDetails other = createUser(uniqueEmail("stranger"));

        mockMvc.perform(post("/api/offers/{id}/decision", offerId).with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACCEPTED"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/offers").with(user(other)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    private static String inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
    }

    private String createApplication() throws Exception {
        String body = mockMvc.perform(post("/api/applications").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","positionTitle":"韌體工程師"}
                                """.formatted(companyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private String createInterview(int round, String scheduledAt) throws Exception {
        String body = mockMvc.perform(post("/api/applications/{id}/interviews", applicationId)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"round":%d,"scheduledAt":"%s"}
                                """.formatted(round, scheduledAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private String createOffer() throws Exception {
        String body = mockMvc.perform(put("/api/applications/{id}/offer", applicationId)
                        .with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":60000,"guaranteedMonths":13}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private org.springframework.test.web.servlet.ResultActions saveReview(
            String interviewId, String notes, short rating, short interest) throws Exception {
        return mockMvc.perform(put("/api/interviews/{id}/review", interviewId)
                .with(user(me)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"processNotes":"%s","rating":%d,"interestLevel":%d}
                        """.formatted(notes, rating, interest)));
    }

    private static String extractId(String json) {
        return json.replaceAll(".*?\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1");
    }
}
