package com.Jolie.career_toolkit.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank @Size(max = 200) String name,
        Short industryId,
        @Size(max = 300) String website,
        String notes
) {}
