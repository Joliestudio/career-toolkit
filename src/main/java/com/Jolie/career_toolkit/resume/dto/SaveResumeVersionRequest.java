package com.Jolie.career_toolkit.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveResumeVersionRequest(
        @NotBlank @Size(max = 120) String label,
        @Size(max = 120) String targetRole
) {}
