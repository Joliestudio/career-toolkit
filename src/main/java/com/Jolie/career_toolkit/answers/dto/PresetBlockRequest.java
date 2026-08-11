package com.Jolie.career_toolkit.answers.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PresetBlockRequest(@NotNull UUID blockId) {}
