package com.Jolie.career_toolkit.answers.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record PresetReorderRequest(@NotEmpty List<UUID> blockIds) {}
