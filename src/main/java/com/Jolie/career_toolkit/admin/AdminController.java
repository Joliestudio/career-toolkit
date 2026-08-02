package com.Jolie.career_toolkit.admin;

import com.Jolie.career_toolkit.auth.dto.UserResponse;
import com.Jolie.career_toolkit.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 後台管理。
 *
 * 這裡刻意用了兩層防護：
 *   1. SecurityConfig 裡的 requestMatchers("/api/admin/**").hasRole("ADMIN")
 *   2. 方法上的 @PreAuthorize
 *
 * 重複不是壞事——第一層是路徑層級的，改 SecurityConfig 時很容易連帶改壞；
 * 第二層跟著程式碼走，就算有人把這個 controller 搬到別的路徑也還在。
 *
 * 值得注意的是這兩層失敗時走的是不同路徑：
 *   requestMatchers 在 AuthorizationFilter 檢查 → ExceptionTranslationFilter → RestAccessDeniedHandler
 *   @PreAuthorize 在方法呼叫時檢查 → 例外在 DispatcherServlet 內拋出 → GlobalExceptionHandler
 * 兩邊都要處理成 403，少一邊就會變成 500。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        // 回 DTO 不回 entity——User entity 有 passwordHash
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
