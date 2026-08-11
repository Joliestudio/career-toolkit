package com.Jolie.career_toolkit.offer.dto;

import com.Jolie.career_toolkit.offer.Offer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        UUID applicationId,
        BigDecimal baseSalary,
        String currency,
        Offer.SalaryPeriod salaryPeriod,
        BigDecimal guaranteedMonths,

        /** 換算成年薪，讓不同結構的 offer 可以放在一起比。 */
        BigDecimal annualisedTotal,

        String bonusNote,
        LocalDate offeredAt,
        LocalDate replyDeadline,

        /** 距離回覆死線還有幾天。負數代表已經過期。null 代表沒設死線。 */
        Long daysUntilDeadline,

        Offer.Decision decision,
        LocalDate decidedAt,
        String declineReason
) {
    public static OfferResponse from(Offer o) {
        Long daysLeft = o.getReplyDeadline() == null
                ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), o.getReplyDeadline());

        return new OfferResponse(
                o.getId(), o.getApplicationId(), o.getBaseSalary(), o.getCurrency(),
                o.getSalaryPeriod(), o.getGuaranteedMonths(), o.annualisedTotal(),
                o.getBonusNote(), o.getOfferedAt(), o.getReplyDeadline(), daysLeft,
                o.getDecision(), o.getDecidedAt(), o.getDeclineReason());
    }
}
