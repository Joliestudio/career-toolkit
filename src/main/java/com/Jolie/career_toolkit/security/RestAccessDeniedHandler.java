package com.Jolie.career_toolkit.security;

// Spring Boot 4 換到 Jackson 3，套件從 com.fasterxml.jackson 改成 tools.jackson。
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * 已登入但權限不足（403）。
 *
 * 401 和 403 的差別要分清楚：
 *   401 = 我不知道你是誰 → 前端應該導向登入頁
 *   403 = 我知道你是誰，但你不能做這件事 → 前端應該顯示「權限不足」
 * 混在一起的話，一個非 admin 使用者點到管理頁會被莫名其妙登出。
 *
 * 注意：這只適用於「角色不足」。對於「別人的資料」一律回 404 而不是 403——
 * 403 等於告訴對方「這個 id 真的存在」，那本身就是資訊洩漏。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "權限不足");
        problem.setType(URI.create("https://career-toolkit.cjinsightflow.com/errors/access-denied"));
        problem.setTitle("Access denied");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
