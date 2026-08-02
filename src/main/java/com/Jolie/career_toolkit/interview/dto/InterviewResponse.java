package com.Jolie.career_toolkit.interview.dto;

import com.Jolie.career_toolkit.interview.Interview;

import java.time.Instant;
import java.util.UUID;

public record InterviewResponse(
        UUID id,
        UUID applicationId,
        Short round,
        Interview.Stage stage,
        Interview.Format format,
        Instant scheduledAt,
        Short durationMinutes,
        String location,
        String interviewers,
        Interview.Status status
) {
    public static InterviewResponse from(Interview i) {
        return new InterviewResponse(
                i.getId(), i.getApplicationId(), i.getRound(), i.getStage(), i.getFormat(),
                i.getScheduledAt(), i.getDurationMinutes(), i.getLocation(),
                i.getInterviewers(), i.getStatus());
    }
}
