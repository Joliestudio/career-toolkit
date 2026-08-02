package com.Jolie.career_toolkit.interview.dto;

import com.Jolie.career_toolkit.interview.Interview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record SaveInterviewRequest(
        @Min(1) @Max(20) short round,
        Instant scheduledAt,
        Interview.Stage stage,
        Interview.Format format,
        Short durationMinutes,
        @Size(max = 300) String location,
        String interviewers,
        Interview.Status status
) {}
