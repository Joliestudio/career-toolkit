package com.Jolie.career_toolkit.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdAndDeletedAtIsNullOrderByStatusChangedAtDesc(UUID userId);

    List<JobApplication> findByUserIdAndStatusAndDeletedAtIsNullOrderByStatusChangedAtDesc(
            UUID userId, ApplicationStatus status);

    Optional<JobApplication> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<JobApplication> findByUserIdAndCompanyIdAndDeletedAtIsNull(UUID userId, UUID companyId);
}
