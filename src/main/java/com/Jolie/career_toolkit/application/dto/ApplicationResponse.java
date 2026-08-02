package com.Jolie.career_toolkit.application.dto;

import com.Jolie.career_toolkit.application.ApplicationStatus;
import com.Jolie.career_toolkit.application.JobApplication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID companyId,
        String companyName,
        String industry,
        String positionTitle,
        String jobUrl,
        String source,
        LocalDate appliedAt,
        ApplicationStatus status,
        /**
         * 從目前狀態能合法轉去哪些狀態。
         *
         * 把它放進回應，前端的下拉選單就只會出現合法選項——
         * 使用者不會送出一個必定被拒的請求，也不用把狀態機在前端再實作一次
         * （兩份實作遲早會不一致）。
         */
        Set<ApplicationStatus> allowedNextStates,
        Instant statusChangedAt,
        Instant nextActionAt,
        String notes,
        UUID resumeVersionId
) {
    public static ApplicationResponse from(JobApplication a, String companyName, String industry) {
        return new ApplicationResponse(
                a.getId(), a.getCompanyId(), companyName, industry,
                a.getPositionTitle(), a.getJobUrl(), a.getSource(),
                a.getAppliedAt(), a.getStatus(), a.getStatus().nextStates(),
                a.getStatusChangedAt(), a.getNextActionAt(), a.getNotes(),
                a.getResumeVersionId());
    }
}
