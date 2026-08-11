package com.Jolie.career_toolkit.parsing.dto;

import com.Jolie.career_toolkit.parsing.ExtractionCandidate;

import java.math.BigDecimal;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        ExtractionCandidate.Kind kind,
        /** 原文中出現的樣子，例如 "k8s" */
        String value,
        /** 正規化後的標準名稱，例如 "Kubernetes" */
        String normalized,
        BigDecimal confidence,
        String extractor,
        ExtractionCandidate.Status status,
        UUID blockId
) {
    public static CandidateResponse from(ExtractionCandidate c) {
        return new CandidateResponse(
                c.getId(), c.getKind(), c.getValue(), c.getNormalized(),
                c.getConfidence(), c.getExtractor(), c.getStatus(), c.getBlockId());
    }
}
