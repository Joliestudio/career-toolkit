package com.Jolie.career_toolkit.auth;

import com.Jolie.career_toolkit.IntegrationTestBase;
import com.Jolie.career_toolkit.user.AppUserDetails;
import com.Jolie.career_toolkit.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AuthControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    // ---------- 註冊 ----------

    @Test
    void register_shouldStoreBcryptHashAndNeverReturnIt() throws Exception {
        String email = uniqueEmail("newbie");

        String body = mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password","displayName":"新人"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        // 回應裡不能有密碼相關的任何東西
        assertThat(body)
                .doesNotContain("password")
                .doesNotContain("a-good-password")
                .doesNotContain("$2a$");

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // DelegatingPasswordEncoder 會加上 {bcrypt} 前綴，
        // 讓日後換演算法可以漸進遷移而不用強迫所有人重設密碼。
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}$2a$");
        // 明文絕對不能出現在資料庫裡
        assertThat(saved.getPasswordHash()).doesNotContain("a-good-password");
    }

    @Test
    void register_shouldRejectDuplicateEmailWith409() throws Exception {
        String email = uniqueEmail("dup");
        String payload = """
                {"email":"%s","password":"a-good-password"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"short"}
                                """.formatted(uniqueEmail("weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"a-good-password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void register_shouldAlwaysCreateUserRoleNeverAdmin() throws Exception {
        String email = uniqueEmail("wannabe-admin");

        // 就算 request 裡塞了 role，也不該生效——角色不是註冊端點能決定的
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password","role":"ADMIN"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    // ---------- 登入 ----------

    /**
     * 這個測試是為了釘住 Spring Security 6+ 最惡名昭彰的坑。
     *
     * 自己寫 JSON 登入端點時，如果只做 SecurityContextHolder.setContext() 而沒有
     * securityContextRepository.saveContext()，登入這個請求會回 200 看起來一切正常，
     * 但下一個請求就 401——因為 context 從來沒被寫進 session。
     *
     * 所以「登入成功」本身證明不了什麼，必須帶著同一個 session 再打一次別的端點。
     */
    @Test
    void login_shouldPersistSecurityContextSoTheNextRequestIsStillAuthenticated() throws Exception {
        String email = uniqueEmail("login");
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // 關鍵：帶著同一個 session 打一個需要登入的端點，必須還是通的
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/api/blocks").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        String email = uniqueEmail("wrongpw");
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"definitely-wrong"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 帳號不存在和密碼錯誤，回應必須一模一樣。
     * 一旦能分辨，任何人都可以拿這個端點逐一測出哪些 email 註冊過（使用者列舉）。
     */
    @Test
    void login_shouldNotRevealWhetherTheEmailExists() throws Exception {
        String existing = uniqueEmail("exists");
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password"}
                                """.formatted(existing)))
                .andExpect(status().isCreated());

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong"}
                                """.formatted(existing)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String noSuchUserBody = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong"}
                                """.formatted(uniqueEmail("ghost"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // timestamp 每次不同，比對其餘部分
        assertThat(stripTimestamp(wrongPasswordBody)).isEqualTo(stripTimestamp(noSuchUserBody));
    }

    // ---------- 登出 ----------

    @Test
    void logout_shouldInvalidateTheSession() throws Exception {
        String email = uniqueEmail("logout");
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-good-password"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // session 已經失效，同一個 session 再打就是 401。
        // 這是 session cookie 勝過 JWT 的地方：撤銷是立即的，JWT 只能等它過期。
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 角色 ----------

    @Test
    void adminEndpoint_shouldReject403ForNormalUser() throws Exception {
        AppUserDetails normalUser = createUser(uniqueEmail("normal"));

        mockMvc.perform(get("/api/admin/users").with(user(normalUser)))
                // 403 不是 401：401 代表「我不知道你是誰」，403 代表「我知道你是誰但你不能做這件事」。
                // 混在一起的話，非 admin 點到管理頁會被莫名其妙登出。
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_shouldAllowAdmin() throws Exception {
        AppUserDetails admin = createAdmin(uniqueEmail("boss"));

        mockMvc.perform(get("/api/admin/users").with(user(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_shouldReject401ForAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserList_shouldNeverContainPasswordHashes() throws Exception {
        AppUserDetails admin = createAdmin(uniqueEmail("boss"));
        createUser(uniqueEmail("victim"));

        String body = mockMvc.perform(get("/api/admin/users").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain("{bcrypt}");
    }

    private static String stripTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"X\"");
    }
}
