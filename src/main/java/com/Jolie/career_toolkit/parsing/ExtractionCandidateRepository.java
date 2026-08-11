package com.Jolie.career_toolkit.parsing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionCandidateRepository extends JpaRepository<ExtractionCandidate, UUID> {

    List<ExtractionCandidate> findByResumeFileIdOrderByConfidenceDesc(UUID resumeFileId);

    List<ExtractionCandidate> findByResumeFileIdAndStatus(
            UUID resumeFileId, ExtractionCandidate.Status status);

    Optional<ExtractionCandidate> findByIdAndUserId(UUID id, UUID userId);

    Optional<ExtractionCandidate> findByResumeFileIdAndKindAndNormalizedAndExtractor(
            UUID resumeFileId, ExtractionCandidate.Kind kind, String normalized, String extractor);
}
