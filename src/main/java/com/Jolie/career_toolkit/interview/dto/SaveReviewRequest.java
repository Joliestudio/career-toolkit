package com.Jolie.career_toolkit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SaveReviewRequest(
        /** 今天的面試流程 */
        String processNotes,
        /** 這份工作實際在做什麼 */
        String jobReality,
        /** 我需要調整的部分 */
        String gaps,
        /** 被問到的題目——之後會變成題型對應的素材 */
        String questionsAsked,
        String redFlags,

        /** 面得好不好 */
        @Min(1) @Max(5) Short rating,
        /** 我多想去。跟 rating 分開，因為兩者經常相反。 */
        @Min(1) @Max(5) Short interestLevel
) {}
