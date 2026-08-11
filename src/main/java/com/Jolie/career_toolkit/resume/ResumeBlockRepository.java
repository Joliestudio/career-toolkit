package com.Jolie.career_toolkit.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 由父實體（resume_version）限定。所有權在 Service 讀取版本時就檢查過了。
 * 分類寫在 RepositoryOwnershipConventionTest 的 PARENT_SCOPED。
 */
public interface ResumeBlockRepository extends JpaRepository<ResumeBlock, ResumeBlock.Key> {

    List<ResumeBlock> findByResumeVersionIdOrderBySortOrderAsc(UUID resumeVersionId);

    Optional<ResumeBlock> findByResumeVersionIdAndBlockId(UUID resumeVersionId, UUID blockId);

    void deleteByResumeVersionIdAndBlockId(UUID resumeVersionId, UUID blockId);
}
