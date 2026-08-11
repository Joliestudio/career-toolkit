package com.Jolie.career_toolkit.parsing.dto;

import com.Jolie.career_toolkit.parsing.ResumeFile;

import java.time.Instant;
import java.util.UUID;

public record ResumeFileResponse(
        UUID id,
        String originalName,
        String contentType,
        long sizeBytes,
        ResumeFile.ParseStatus parseStatus,
        /** 解析不成功時要說得出原因——尤其「這是掃描檔」跟「解析失敗」是不同的事 */
        String parseError,
        Instant createdAt
) {
    public static ResumeFileResponse from(ResumeFile f) {
        return new ResumeFileResponse(
                f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                f.getParseStatus(), f.getParseError(), f.getCreatedAt());
    }
}
