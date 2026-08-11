package com.Jolie.career_toolkit.resume.dto;

import com.Jolie.career_toolkit.resume.ResumeVersion;

import java.time.Instant;
import java.util.UUID;

public record ResumeVersionResponse(
        UUID id,
        String label,
        String targetRole,
        String locale,
        /** 鎖定後就不能再改。前端要靠這個把編輯功能收起來。 */
        boolean locked,
        Instant lockedAt,
        UUID parentId,
        Instant updatedAt
) {
    public static ResumeVersionResponse from(ResumeVersion v) {
        return new ResumeVersionResponse(
                v.getId(), v.getLabel(), v.getTargetRole(), v.getLocale(),
                v.isLocked(), v.getLockedAt(), v.getParentId(), v.getUpdatedAt());
    }
}
