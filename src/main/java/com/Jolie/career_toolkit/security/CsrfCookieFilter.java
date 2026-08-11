package com.Jolie.career_toolkit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CsrfToken 在 Spring Security 6 之後是「延遲載入」的：
 * 沒有任何程式碼真的去讀它的值，CookieCsrfTokenRepository 就不會把 cookie 寫出去。
 *
 * 結果就是前端永遠拿不到 XSRF-TOKEN cookie，於是每一個 POST 都被擋成 403，
 * 而且完全看不出原因——這是換到 Security 6+ 最常見的卡點之一。
 *
 * 這個 filter 只做一件事：主動呼叫一次 getToken()，強迫 cookie 被寫進回應。
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();   // 這一行就是重點：讀了才會寫 cookie
        }

        filterChain.doFilter(request, response);
    }
}
