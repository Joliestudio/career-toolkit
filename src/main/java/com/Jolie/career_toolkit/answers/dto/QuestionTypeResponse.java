package com.Jolie.career_toolkit.answers.dto;

import com.Jolie.career_toolkit.answers.QuestionType;

public record QuestionTypeResponse(Short id, String code, String name, String hint) {

    public static QuestionTypeResponse from(QuestionType t) {
        return new QuestionTypeResponse(t.getId(), t.getCode(), t.getNameZh(), t.getHint());
    }
}
