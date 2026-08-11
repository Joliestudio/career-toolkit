package com.Jolie.career_toolkit.portfolio;

import com.Jolie.career_toolkit.portfolio.dto.ProjectResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 公開的作品集。**這是整個系統唯一不需要登入的端點。**
 *
 * 三條規則，每一條少了都會變成資料外洩：
 *   1. 只回 is_public = true 的專案（過濾寫在 SQL 的 WHERE 裡，不是撈回來再篩）
 *   2. 回傳專用的 Public 投影，明確列出要公開的欄位——
 *      不重用內部的 ProjectResponse，否則之後有人在 Project 加了私人欄位就會自動洩漏
 *   3. 絕不回傳使用者本人的任何資訊（email、顯示名稱都不行）
 *
 * 路徑放在 /api/public/** 底下，SecurityConfig 才好用一條規則放行，
 * 不會不小心把別的東西一起開出去。
 */
@RestController
@RequestMapping("/api/public")
public class PublicPortfolioController {

    private final ProjectService projectService;

    public PublicPortfolioController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/portfolio/{userId}")
    public List<ProjectResponse.Public> portfolio(@PathVariable UUID userId) {
        return projectService.listPublic(userId).stream()
                .map(ProjectResponse.Public::from)
                .toList();
    }
}
