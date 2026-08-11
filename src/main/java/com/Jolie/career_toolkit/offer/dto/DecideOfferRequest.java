package com.Jolie.career_toolkit.offer.dto;

import com.Jolie.career_toolkit.offer.Offer;
import jakarta.validation.constraints.NotNull;

public record DecideOfferRequest(
        @NotNull Offer.Decision decision,
        /** 婉拒理由。三個月後回頭看「我為什麼拒掉那家」，沒寫下來就只剩模糊印象。 */
        String declineReason
) {}
