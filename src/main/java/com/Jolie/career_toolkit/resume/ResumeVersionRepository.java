package com.Jolie.career_toolkit.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {

    List<ResumeVersion> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

    Optional<ResumeVersion> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
