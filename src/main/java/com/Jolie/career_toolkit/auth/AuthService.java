package com.Jolie.career_toolkit.auth;

import com.Jolie.career_toolkit.auth.dto.RegisterRequest;
import com.Jolie.career_toolkit.user.Role;
import com.Jolie.career_toolkit.user.User;
import com.Jolie.career_toolkit.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        // encode 的結果會帶 {bcrypt} 前綴，例如 {bcrypt}$2a$10$...
        // 前綴讓日後換演算法可以漸進遷移，不用強迫所有人重設密碼。
        String hash = passwordEncoder.encode(request.password());

        // 註冊一律是 USER。要有 ADMIN 只能由既有的 admin 手動升級，
        // 不能讓註冊端點決定角色——那等於開放任何人自封管理員。
        User user = new User(request.email(), hash, request.displayName(), Role.USER);

        return userRepository.save(user);
    }
}
