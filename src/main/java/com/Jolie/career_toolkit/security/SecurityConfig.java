package com.Jolie.career_toolkit.security;

import com.Jolie.career_toolkit.user.AppUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security 7.1（Boot 4.1 帶的版本）。
 *
 * 網路上的教學要特別小心版本：
 *   Security 5 的寫法（WebSecurityConfigurerAdapter、antMatchers()、authorizeRequests()）
 *     已經被刪除，連編譯都過不了。
 *   Security 6 的寫法方向正確（SecurityFilterChain bean + lambda DSL），
 *     但 6 裡面標記 deprecated 的 API 在 7 已經移除，所以 6 的範例也可能編不過。
 * 唯一可靠的來源是官方文件，而且要確認版本。
 */
@Configuration
@EnableMethodSecurity   // 讓 @PreAuthorize 生效
public class SecurityConfig {

    /**
     * DelegatingPasswordEncoder：雜湊值會帶著 {bcrypt} 前綴存進資料庫。
     *
     * 為什麼不直接用 new BCryptPasswordEncoder()：帶前綴的話，日後要換成
     * argon2 之類的演算法時，新密碼用新演算法、舊密碼仍然驗得過，可以漸進遷移，
     * 不需要強迫所有人重設密碼。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 帳號不存在時也要跑一次密碼雜湊比對，讓「帳號不存在」和「密碼錯誤」的
        // 回應時間一致。否則攻擊者可以用回應時間差判斷哪些 email 有註冊過。
        provider.setHideUserNotFoundExceptions(true);

        return provider::authenticate;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RestAuthenticationEntryPoint entryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        // ---- CSRF：維持開啟 ----
        // 想避開 CSRF 是選 JWT 最常見的「錯誤理由」。用 session cookie 就要正面處理它。
        //
        // withHttpOnlyFalse() 讓瀏覽器端的 JavaScript 讀得到 XSRF-TOKEN cookie，
        // 前端再把值原封不動放進 X-XSRF-TOKEN header 送回來。
        //
        // 為什麼用 CsrfTokenRequestAttributeHandler 而不是預設的 XOR 版本：
        // XOR 版本會把 token 遮罩後才交給客戶端（防 BREACH 攻擊），但 CookieCsrfTokenRepository
        // 寫進 cookie 的是「原始」token。兩者搭在一起時，前端從 cookie 讀到原始值送回來，
        // 伺服器卻用 XOR 去解，就會對不上——這是 SPA 接 Spring Security 6+ 最常見的 403。
        //
        // 官方文件的解法是只借用 XOR 的「產生」邏輯、解析沿用原始值（delegate::handle），
        // 但那樣寫會跟測試用的 SecurityMockMvcRequestPostProcessors.csrf() 衝突：
        // 它送的是遮罩值，而解析端期待原始值。
        //
        // 這裡選擇兩端都用原始值，前後一致。代價是失去 XOR 的 BREACH 防護——
        // 而 BREACH 需要「token 出現在會被壓縮的回應內容裡」才成立。
        // 這是純 JSON API，token 只走 cookie 與 header，從不出現在回應 body，所以那層防護在這裡沒有作用對象。
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // 註冊與登入還沒有 session，無法先取得 token
                        .ignoringRequestMatchers("/api/auth/register", "/api/auth/login"))

                // CsrfToken 是延遲載入的：沒有人碰它，cookie 就不會被寫出去。
                // 這個 filter 主動讀一次 token，確保每個回應都帶著 XSRF-TOKEN cookie。
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // 公開作品集是整個系統唯一不需要登入的端點。
                        // 集中在 /api/public/** 底下，才不會不小心把別的東西一起開出去。
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        // Cloudflare Tunnel 的健康檢查會打這裡，它不會帶 session cookie。
                        // 只有 health 這一個 endpoint，而且 show-details: never，
                        // 所以回應只有 {"status":"UP"}，沒有任何可以拿來偵察的資訊。
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 其餘（前端靜態資源）先全部放行，P2 會把 SPA build 進 static/
                        .anyRequest().permitAll())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // 登入後換一個新的 session id，擋 session fixation 攻擊
                        .sessionFixation(fixation -> fixation.changeSessionId()))

                .exceptionHandling(ex -> ex
                        // 預設行為是導向登入頁（302）。對 JSON API 來說那是錯的——
                        // 前端拿到的會是一頁 HTML 而不是 401。
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // 這是 JSON API，不要表單登入頁，也不要瀏覽器彈出的 Basic 認證視窗
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());   // 自己在 AuthController 實作

        return http.build();
    }
}
