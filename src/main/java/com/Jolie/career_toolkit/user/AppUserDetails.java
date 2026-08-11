package com.Jolie.career_toolkit.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 自訂的 UserDetails，重點是它帶著 userId。
 *
 * Spring Security 內建的 User 只有 username（我們用 email）。如果只有 email，
 * Service 層每次要用 userId 都得再查一次資料庫——每個請求多一次查詢，
 * 而且會有人為了省事直接拿 email 當外鍵，那是更糟的設計。
 *
 * 必須 Serializable：session 存進資料庫時要序列化成 bytea。
 * 少了它會在登入後的第一個請求爆 NotSerializableException。
 */
public record AppUserDetails(
        UUID id,
        String email,
        String passwordHash,
        Role role
) implements UserDetails, Serializable {

    public static AppUserDetails from(User user) {
        return new AppUserDetails(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.asAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security 的「username」在這個系統裡就是 email。 */
    @Override
    public String getUsername() {
        return email;
    }
}
