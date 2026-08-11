package com.Jolie.career_toolkit.portfolio.dto;

import com.Jolie.career_toolkit.portfolio.Project;

import java.time.LocalDate;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String summary,
        String repoUrl,
        String demoUrl,
        String techStack,
        String role,
        LocalDate startedOn,
        LocalDate endedOn,
        Boolean isPublic,
        Integer sortOrder,
        UUID blockId
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getSummary(), p.getRepoUrl(), p.getDemoUrl(),
                p.getTechStack(), p.getRole(), p.getStartedOn(), p.getEndedOn(),
                p.getIsPublic(), p.getSortOrder(), p.getBlockId());
    }

    /**
     * 公開頁專用的投影。
     *
     * 刻意做成獨立的方法而不是重用上面那個：公開端點回傳的欄位必須是
     * 「明確列出來的」，不能是「把內部 DTO 拿去用、希望裡面剛好沒有敏感欄位」。
     * 之後有人在 Project 加了 privateNotes 之類的欄位，這裡不會自動跟著洩漏。
     */
    public record Public(
            UUID id,
            String name,
            String summary,
            String repoUrl,
            String demoUrl,
            String techStack,
            String role,
            LocalDate startedOn,
            LocalDate endedOn
    ) {
        public static Public from(Project p) {
            return new Public(
                    p.getId(), p.getName(), p.getSummary(), p.getRepoUrl(), p.getDemoUrl(),
                    p.getTechStack(), p.getRole(), p.getStartedOn(), p.getEndedOn());
        }
    }
}
