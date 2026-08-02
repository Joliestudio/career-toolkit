package com.Jolie.career_toolkit.common;

import com.Jolie.career_toolkit.application.IllegalStatusTransitionException;
import com.Jolie.career_toolkit.auth.EmailAlreadyRegisteredException;
import com.Jolie.career_toolkit.block.BlockNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 統一的錯誤回應格式，用 Spring 內建的 ProblemDetail（RFC 7807 標準）。
 *
 * 為什麼要統一格式：前端需要能「程式化」處理錯誤。如果 404 回純文字、400 回 JSON、
 * 500 回 HTML，前端就得寫三套解析邏輯，最後只好對所有失敗都顯示「發生錯誤」。
 *
 * 這一課看起來很小，但它是「玩具」和「產品」的分界。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BASE_TYPE = "https://career-toolkit.cjinsightflow.com/errors/";

    @ExceptionHandler(BlockNotFoundException.class)
    public ProblemDetail handleBlockNotFound(BlockNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "找不到指定的積木");
        problem.setType(URI.create(BASE_TYPE + "block-not-found"));
        problem.setTitle("Block not found");
        decorate(problem, request);

        // 404 是預期中的情況，不是系統故障——用 debug 而不是 error，
        // 否則正常使用就會把 log 洗滿，真正的錯誤反而被淹掉。
        log.debug("Block not found: {}", ex.getBlockId());
        return problem;
    }

    /** 找不到資源——包含「不屬於你」的情況，一律 404 不 403。 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex,
                                                HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "找不到指定的資料");
        problem.setType(URI.create(BASE_TYPE + "not-found"));
        problem.setTitle(ex.getResource() + " not found");
        decorate(problem, request);

        log.debug("Resource not found: {}", ex.getMessage());
        return problem;
    }

    /**
     * 非法的狀態轉換。
     *
     * 錯誤訊息一定要說得出「那我可以改成什麼」——只說「不能這樣改」的話，
     * 使用者只能一個一個試。allowed 欄位讓前端可以直接把選項改對。
     */
    @ExceptionHandler(IllegalStatusTransitionException.class)
    public ProblemDetail handleIllegalTransition(IllegalStatusTransitionException ex,
                                                 HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.describeAllowed());
        problem.setType(URI.create(BASE_TYPE + "illegal-status-transition"));
        problem.setTitle("Illegal status transition");
        problem.setProperty("from", ex.getFrom());
        problem.setProperty("to", ex.getTo());
        problem.setProperty("allowed", ex.getAllowed());
        decorate(problem, request);

        return problem;
    }

    /**
     * 登入失敗。
     *
     * 訊息刻意寫成「帳號或密碼錯誤」，不區分是哪一個。
     * 一旦區分開來，任何人都可以拿這個端點逐一測試哪些 email 有註冊過
     * ——這叫使用者列舉（user enumeration）。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure(AuthenticationException ex,
                                                     HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤");
        problem.setType(URI.create(BASE_TYPE + "bad-credentials"));
        problem.setTitle("Authentication failed");
        decorate(problem, request);

        log.debug("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem;
    }

    /**
     * 權限不足。
     *
     * 為什麼需要這個 handler：SecurityConfig 裡用 requestMatchers 設的規則是在
     * AuthorizationFilter 檢查的（DispatcherServlet 之前），拋出的例外由
     * ExceptionTranslationFilter 交給 RestAccessDeniedHandler，不會經過這裡。
     *
     * 但 @PreAuthorize 是在方法被呼叫時檢查的，例外在 DispatcherServlet 內部拋出，
     * 會直接掉進 @RestControllerAdvice——少了這個 handler 就會變成 500 而不是 403。
     * 同一種「權限不足」，走兩條不同的路徑，這件事很容易漏。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "權限不足");
        problem.setType(URI.create(BASE_TYPE + "access-denied"));
        problem.setTitle("Access denied");
        decorate(problem, request);

        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException ex,
                                          HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "這個 email 已經註冊過了");
        problem.setType(URI.create(BASE_TYPE + "email-already-registered"));
        problem.setTitle("Email already registered");
        decorate(problem, request);

        return problem;
    }

    /**
     * Bean Validation 失敗（@NotBlank / @Size 等）。
     * 一定要說得出「是哪個欄位錯了」，否則前端只能顯示「輸入有誤」，使用者不知道要改哪裡。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException ex,
                                                 HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.merge(error.getField(), error.getDefaultMessage(),
                        (existing, incoming) -> existing + "; " + incoming));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "有 %d 個欄位不符合規則".formatted(fieldErrors.size()));
        problem.setType(URI.create(BASE_TYPE + "validation-failed"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", fieldErrors);
        decorate(problem, request);

        return problem;
    }

    /** 路徑或查詢參數型別不對，例如 /api/blocks/not-a-uuid 或 ?type=NOT_A_TYPE。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "參數 '%s' 的格式不正確".formatted(ex.getName()));
        problem.setType(URI.create(BASE_TYPE + "invalid-parameter"));
        problem.setTitle("Invalid parameter");
        problem.setProperty("parameter", ex.getName());
        decorate(problem, request);

        return problem;
    }

    /** JSON 本身壞掉（少括號、型別對不上）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex,
                                              HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "請求內容無法解析，請確認是合法的 JSON");
        problem.setType(URI.create(BASE_TYPE + "malformed-request"));
        problem.setTitle("Malformed request body");
        decorate(problem, request);

        // 原始訊息可能包含 Java 類別名與欄位結構，只進 log 不回給前端
        log.warn("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem;
    }

    /**
     * 兜底。訊息寫死成通用字串，絕對不能把 exception message 回給前端。
     *
     * 原因：exception message 常常包含 SQL 語句、表名、檔案路徑、內部類別名。
     * 這些對攻擊者是免費的情報。
     *
     * 但錯誤本身不能就這樣消失，否則沒人有辦法除錯——所以做法是：
     * 產生一個 errorId，完整的 stack trace 帶著這個 id 寫進 log，
     * 回給前端的只有這個 id。使用者回報「我看到錯誤 a1b2c3」就能直接在 log 裡撈到現場。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {

        // Spring MVC 自己拋的例外——找不到路由、HTTP method 不支援、缺少必要參數、
        // Content-Type 不被接受——在 Spring 6 之後都實作了 ErrorResponse 介面，
        // 本身就帶著正確的狀態碼與 ProblemDetail body。要先放它們走。
        //
        // 少了這一段會發生什麼：它們全部被下面的 500 吃掉，
        // 打一個不存在的網址回 500 而不是 404，用錯 method 回 500 而不是 405。
        //
        // 這個洞是把應用跑進容器之後隨手打幾個不存在的路徑才發現的——
        // 在那之前 27 個測試全綠，因為測試打的都是「存在的」端點。
        // 兜底 handler 的風險就在這裡：它會把你沒想到的東西一起吃掉。
        //
        //（另一種寫法是讓這個類別繼承 ResponseEntityExceptionHandler，
        //  由框架處理那二十幾種標準例外。這裡選擇顯式判斷，因為意圖看得見。
        //  注意 ErrorResponse 是介面不是 Throwable，不能直接寫成 @ExceptionHandler 的參數。）
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail problem = errorResponse.getBody();
            decorate(problem, request);
            return ResponseEntity.status(errorResponse.getStatusCode()).body(problem);
        }

        String errorId = UUID.randomUUID().toString().substring(0, 8);

        log.error("Unhandled exception [errorId={}] on {} {}",
                errorId, request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "系統發生錯誤，請稍後再試");
        problem.setType(URI.create(BASE_TYPE + "internal-error"));
        problem.setTitle("Internal server error");
        problem.setProperty("errorId", errorId);
        decorate(problem, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private void decorate(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
    }
}
