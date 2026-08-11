package com.Jolie.career_toolkit.answers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerPresetRepository extends JpaRepository<AnswerPreset, UUID> {

    List<AnswerPreset> findByUserIdOrderByQuestionTypeIdAsc(UUID userId);

    Optional<AnswerPreset> findByIdAndUserId(UUID id, UUID userId);
}
