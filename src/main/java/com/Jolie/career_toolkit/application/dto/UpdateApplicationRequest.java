package com.Jolie.career_toolkit.application.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** 部分更新：null 代表「這個欄位不要動」，不是「清空」。 */
public record UpdateApplicationRequest(
        @Size(max = 200) String positionTitle,
        @Size(max = 500) String jobUrl,
        @Size(max = 40) String source,
        LocalDate appliedAt,
        Instant nextActionAt,
        String notes
) {}
