package com.Jolie.career_toolkit.application.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 部分更新：null 代表「這個欄位不要動」，不是「清空」。 */
public record UpdateApplicationRequest(
        @Size(max = 200) String positionTitle,
        @Size(max = 500) String jobUrl,
        @Size(max = 40) String source,
        LocalDate appliedAt,
        Instant nextActionAt,
        String notes,
        /**
         * 這次投遞用了哪一份履歷版本。
         *
         * 資料庫有一個複合外鍵 (user_id, resume_version_id)，
         * 所以就算 Service 的檢查漏掉，也不可能掛到別人的履歷版本。
         */
        UUID resumeVersionId
) {}
