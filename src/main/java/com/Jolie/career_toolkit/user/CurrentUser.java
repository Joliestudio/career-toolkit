package com.Jolie.career_toolkit.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 取得目前登入使用者的唯一入口。
 *
 * 最重要的規則：userId 只能從這裡拿，絕對不能從 request body、path variable
 * 或 query parameter 拿。從客戶端送來的 userId 就是漏洞本身——
 * 任何人都可以改成別人的 id。
 *
 * 集中成一個元件而不是到處寫 SecurityContextHolder.getContext()，
 * 是為了讓「哪裡讀了身分」這件事可以被搜尋、被測試、之後也好換實作。
 */
@Component
public class CurrentUser {

    public UUID id() {
        return details().id();
    }

    public String email() {
        return details().email();
    }

    public Role role() {
        return details().role();
    }

    public boolean isAdmin() {
        return role() == Role.ADMIN;
    }

    private AppUserDetails details() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails details)) {
            // 走到這裡代表 SecurityFilterChain 設定有漏洞——某個端點沒有要求認證，
            // 卻呼叫了需要身分的程式碼。這種情況要立刻炸掉，不能靜默回 null，
            // 否則會變成「userId 是 null」一路往下傳，最後查出別人的資料或整組資料。
            throw new IllegalStateException(
                    "沒有已認證的使用者。這個端點應該要求登入，請檢查 SecurityConfig。");
        }

        return details;
    }
}
