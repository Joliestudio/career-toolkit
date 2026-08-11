package com.Jolie.career_toolkit.answers.dto;

import com.Jolie.career_toolkit.answers.AnswerPreset;

import java.util.UUID;

public record AnswerPresetResponse(
        UUID id,
        Short questionTypeId,
        String label,
        Integer targetCharLimit,
        String notes
) {
    public static AnswerPresetResponse from(AnswerPreset p) {
        return new AnswerPresetResponse(
                p.getId(), p.getQuestionTypeId(), p.getLabel(), p.getTargetCharLimit(), p.getNotes());
    }
}
