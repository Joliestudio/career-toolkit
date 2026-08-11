package com.Jolie.career_toolkit.block.dto;
import jakarta.validation.constraints.Size;

public record UpdateBlockRequest (
    @Size(max = 200) String title,
    String content
) {}
