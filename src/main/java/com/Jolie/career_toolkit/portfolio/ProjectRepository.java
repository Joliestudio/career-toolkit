package com.Jolie.career_toolkit.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID userId);

    /**
     * 公開作品集頁用的查詢。
     *
     * isPublic 這個條件寫在方法名裡而不是撈回來之後才篩——
     * 撈回來再篩的話，只要哪天有人忘了那行 filter，私人專案就直接公開了，
     * 而且不會有任何錯誤訊息。
     */
    List<Project> findByUserIdAndIsPublicTrueAndDeletedAtIsNullOrderBySortOrderAsc(UUID userId);

    Optional<Project> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
