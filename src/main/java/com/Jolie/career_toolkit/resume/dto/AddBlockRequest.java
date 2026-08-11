package com.Jolie.career_toolkit.resume.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddBlockRequest(
        @NotNull UUID blockId,
        @Size(max = 40) String section
) {}
