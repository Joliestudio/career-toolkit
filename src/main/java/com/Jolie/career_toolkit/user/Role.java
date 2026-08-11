package com.Jolie.career_toolkit.user;

/**
 * 存進資料庫時用字串（@Enumerated(EnumType.STRING)），不用 ordinal。
 * 用 ordinal 的話，日後在 enum 中間插入一個值就會讓所有既有資料的意義整個位移，
 * 而且不會有任何錯誤訊息。
 */
public enum Role {
    USER,
    ADMIN;

    /** Spring Security 的慣例是權限字串要有 ROLE_ 前綴，hasRole("ADMIN") 才對得上。 */
    public String asAuthority() {
        return "ROLE_" + name();
    }
}
