package com.Jolie.career_toolkit.parsing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeFileRepository extends JpaRepository<ResumeFile, UUID> {

    List<ResumeFile> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ResumeFile> findByIdAndUserId(UUID id, UUID userId);

    /** 同一個人重複上傳同一份檔案時直接回既有那筆，不要堆出重複的候選。 */
    Optional<ResumeFile> findByUserIdAndSha256(UUID userId, String sha256);
}
