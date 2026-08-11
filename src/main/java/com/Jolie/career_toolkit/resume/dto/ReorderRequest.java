package com.Jolie.career_toolkit.resume.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** 前端送一份完整的、已排好的清單，後端整份重寫 sort_order。 */
public record ReorderRequest(
        @NotEmpty List<UUID> blockIds
) {}
