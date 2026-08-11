package com.Jolie.career_toolkit.offer.dto;

import com.Jolie.career_toolkit.offer.Offer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaveOfferRequest(
        @DecimalMin("0.0") BigDecimal baseSalary,
        @Size(min = 3, max = 3) String currency,
        Offer.SalaryPeriod salaryPeriod,

        /**
         * 保證年薪幾個月。
         * 台灣的 offer 少了這欄根本沒辦法比較：月薪 50k × 14 ≠ 月薪 55k × 12。
         */
        @DecimalMin("0.0") BigDecimal guaranteedMonths,

        String bonusNote,
        LocalDate offeredAt,
        LocalDate replyDeadline
) {}
