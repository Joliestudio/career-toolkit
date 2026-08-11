package com.Jolie.career_toolkit.block.dto;

import com.Jolie.career_toolkit.block.BlockType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBlockRequest(
        @NotNull BlockType type,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {}