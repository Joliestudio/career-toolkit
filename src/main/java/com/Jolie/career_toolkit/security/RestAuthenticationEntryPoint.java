package com.Jolie.career_toolkit.security;

// Spring Boot 4 換到 Jackson 3，套件從 com.fasterxml.jackson 改成 tools.jackson。
// （只有 jackson-annotations 還留在 com.fasterxml.jackson.annotation。）
// 所有 Boot 3.x 教學裡的 Jackson import 在這裡都會是錯的。
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * 未登入時的回應。
 *
 * Spring Security 的預設行為是導向登入頁（302）。對 JSON API 來說那是錯的：
 * 前端的 fetch 會拿到一頁 HTML 而不是 401，判斷不出「要重新登入」這件事。
 *
 * 格式跟 GlobalExceptionHandler 一致，都用 ProblemDetail——
 * 統一格式的意義就在這裡，前端只需要一套解析邏輯。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "請先登入");
        problem.setType(URI.create("https://career-toolkit.cjinsightflow.com/errors/unauthenticated"));
        problem.setTitle("Unauthenticated");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
