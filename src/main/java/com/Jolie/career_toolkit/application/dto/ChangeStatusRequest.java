package com.Jolie.career_toolkit.application.dto;

import com.Jolie.career_toolkit.application.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull ApplicationStatus status,
        /** 為什麼改成這個狀態。會一起存進歷程，之後回頭看才知道當時發生什麼事。 */
        String note
) {}
