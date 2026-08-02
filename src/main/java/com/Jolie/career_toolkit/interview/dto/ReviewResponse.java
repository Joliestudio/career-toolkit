package com.Jolie.career_toolkit.interview.dto;

import com.Jolie.career_toolkit.interview.Review;

import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID interviewId,
        String processNotes,
        String jobReality,
        String gaps,
        String questionsAsked,
        String redFlags,
        Short rating,
        Short interestLevel
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(), r.getInterviewId(), r.getProcessNotes(), r.getJobReality(),
                r.getGaps(), r.getQuestionsAsked(), r.getRedFlags(),
                r.getRating(), r.getInterestLevel());
    }
}
