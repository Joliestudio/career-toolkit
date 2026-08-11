package com.Jolie.career_toolkit.portfolio.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SaveProjectRequest(
        @Size(max = 200) String name,
        String summary,
        @Size(max = 500) String repoUrl,
        @Size(max = 500) String demoUrl,
        String techStack,
        @Size(max = 200) String role,
        LocalDate startedOn,
        LocalDate endedOn,
        /** 預設 false —— 公開必須是明確的動作。 */
        Boolean isPublic,
        Integer sortOrder,
        UUID blockId
) {}
