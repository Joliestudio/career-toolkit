package com.Jolie.career_toolkit.application.dto;

import com.Jolie.career_toolkit.application.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID companyId,
        @NotBlank @Size(max = 200) String positionTitle,
        @Size(max = 500) String jobUrl,
        @Size(max = 40) String source,
        LocalDate appliedAt,
        /** 不給就預設 APPLIED。想先記下還沒投的職缺就給 DRAFT。 */
        ApplicationStatus status,
        String notes
) {}
