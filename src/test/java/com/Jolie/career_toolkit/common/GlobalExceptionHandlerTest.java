package com.Jolie.career_toolkit.common;

import com.Jolie.career_toolkit.IntegrationTestBase;
import com.Jolie.career_toolkit.block.BlockNotFoundException;
import com.Jolie.career_toolkit.block.BlockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用 @MockitoBean 取代真正的 BlockService，才能刻意讓它拋出各種例外。
 *
 * 注意：@MockBean 在 Spring Boot 4 已經被移除（spring-boot-test-4.1.0.jar 裡沒有這個類別），
 * 替代品是 spring-test 的 @MockitoBean。所有 Boot 3.x 的教學在這裡都會編譯失敗。
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockService blockService;

    @Test
    void notFound_shouldReturn404AsProblemDetail() throws Exception {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000999");
        given(blockService.getBlock(missing)).willThrow(new BlockNotFoundException(missing));

        mockMvc.perform(get("/api/blocks/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Block not found"))
                .andExpect(jsonPath("$.type").value(
                        "https://career-toolkit.cjinsightflow.com/errors/block-not-found"))
                .andExpect(jsonPath("$.instance").value("/api/blocks/" + missing))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void validationFailure_shouldReturn400AndNameTheOffendingField() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"","content":"x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                // 必須說得出是哪個欄位錯了，否則前端只能顯示「輸入有誤」
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void validationFailure_shouldReportEveryOffendingField() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SKILL","title":"","content":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void invalidUuidInPath_shouldReturn400NotServerError() throws Exception {
        mockMvc.perform(get("/api/blocks/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void malformedJson_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SKILL\",")) // 少了收尾
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    /**
     * 兜底 handler 最容易犯的錯：把 Spring MVC 自己的例外一起吃掉。
     *
     * 這兩個測試是在把應用跑進容器、隨手打幾個不存在的路徑之後補的——
     * 在那之前所有測試都是打「存在的」端點，27 個全綠，但打任何不存在的網址都回 500。
     */
    @Test
    void unknownPath_shouldReturn404NotServerError() throws Exception {
        mockMvc.perform(get("/api/definitely-not-a-real-endpoint"))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongHttpMethod_shouldReturn405NotServerError() throws Exception {
        UUID id = UUID.randomUUID();

        // /api/blocks/{id} 只支援 GET / PATCH / DELETE，沒有 POST
        mockMvc.perform(post("/api/blocks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * 這是整個 P0-c 最重要的測試。
     *
     * exception message 常常包含 SQL 語句、表名、檔案路徑、內部類別名，
     * 這些對攻擊者是免費的情報。兜底的 500 必須把它們全部擋在伺服器內。
     */
    @Test
    void unexpectedException_shouldNotLeakInternalDetailsToClient() throws Exception {
        String leakyMessage =
                "SELECT password_hash FROM users WHERE id = 1 "
                        + "[org.postgresql.jdbc.PgConnection] at /var/lib/postgresql/data";
        given(blockService.getBlocks(any()))
                .willThrow(new IllegalStateException(leakyMessage));

        String body = mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("系統發生錯誤，請稍後再試"))
                // errorId 讓使用者回報時能對應到 log 裡的完整 stack trace
                .andExpect(jsonPath("$.errorId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 逐項確認沒有洩漏
        assertThat(body)
                .doesNotContain("password_hash")
                .doesNotContain("SELECT")
                .doesNotContain("postgresql")
                .doesNotContain("/var/lib")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.Jolie");
    }
}
