package com.Jolie.career_toolkit.application.dto;

import com.Jolie.career_toolkit.application.ApplicationStatus;
import com.Jolie.career_toolkit.application.ApplicationStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(
        UUID id,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        Instant changedAt,
        String note
) {
    public static StatusHistoryResponse from(ApplicationStatusHistory h) {
        return new StatusHistoryResponse(
                h.getId(), h.getFromStatus(), h.getToStatus(), h.getChangedAt(), h.getNote());
    }
}
