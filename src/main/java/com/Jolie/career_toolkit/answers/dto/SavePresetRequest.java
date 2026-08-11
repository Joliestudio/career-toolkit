package com.Jolie.career_toolkit.answers.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SavePresetRequest(
        Short questionTypeId,
        @NotBlank @Size(max = 120) String label,
        /** 目標字數上限。很多表單限 300 或 500 字。 */
        @Min(1) Integer targetCharLimit,
        String notes
) {}
