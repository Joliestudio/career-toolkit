package com.Jolie.career_toolkit.auth.dto;

import com.Jolie.career_toolkit.user.AppUserDetails;
import com.Jolie.career_toolkit.user.Role;
import com.Jolie.career_toolkit.user.User;

import java.util.UUID;

/**
 * 這個 record 裡沒有 passwordHash，而且永遠不會有。
 * Entity 不能直接當 API 回傳值，最直接的理由就是這個：直接回傳 User 就是直接洩漏密碼雜湊。
 */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    public static UserResponse from(AppUserDetails details) {
        return new UserResponse(details.id(), details.email(), null, details.role());
    }
}
