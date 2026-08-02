package com.Jolie.career_toolkit.auth;

import com.Jolie.career_toolkit.auth.dto.LoginRequest;
import com.Jolie.career_toolkit.auth.dto.RegisterRequest;
import com.Jolie.career_toolkit.auth.dto.UserResponse;
import com.Jolie.career_toolkit.user.AppUserDetails;
import com.Jolie.career_toolkit.user.CurrentUser;
import com.Jolie.career_toolkit.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final CurrentUser currentUser;

    // SecurityContext 存進 HttpSession，而 session 由 Spring Session JDBC 存進資料庫。
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          CurrentUser currentUser) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(201).body(UserResponse.from(user));
    }

    /**
     * 自己寫的 JSON 登入端點。
     *
     * ⚠️ 這裡是 Spring Security 6 之後最惡名昭彰的坑：
     * SecurityContext 不再會被自動儲存。只做到 SecurityContextHolder.setContext() 的話，
     * 這一個請求裡看起來一切正常、回應 200、使用者資料也對，
     * 但下一個請求就 401——因為 context 從來沒有被寫進 session。
     *
     * 症狀是「登入成功，然後立刻又被登出」，而且沒有任何錯誤訊息。
     * 解法就是下面那行 securityContextRepository.saveContext()。
     *
     * （用內建的 formLogin 不會遇到這個問題，因為 filter 幫你做了。
     *   一旦自己寫登入端點，這件事就變成你的責任。）
     */
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 少了這一行 → 登入成功但下一個請求 401
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return UserResponse.from((AppUserDetails) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // SecurityContextLogoutHandler 會讓 session 失效並清空 SecurityContext。
        // session 是存在資料庫裡的，所以這一步是「真的」撤銷——
        // 這正是 session cookie 勝過 JWT 的地方：JWT 只能等它過期。
        new SecurityContextLogoutHandler().logout(request, response, authentication);

        return ResponseEntity.noContent().build();
    }

    /** 前端啟動時用來確認「我還在登入狀態嗎」。 */
    @GetMapping("/me")
    public UserResponse me() {
        return new UserResponse(currentUser.id(), currentUser.email(), null, currentUser.role());
    }
}
