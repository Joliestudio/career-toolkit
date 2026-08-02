package com.Jolie.career_toolkit.company.dto;

import com.Jolie.career_toolkit.company.Company;
import com.Jolie.career_toolkit.company.Industry;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        Short industryId,
        String industry,
        String website,
        Boolean verified
) {
    public static CompanyResponse from(Company company, Industry industry) {
        return new CompanyResponse(
                company.getId(), company.getName(), company.getIndustryId(),
                industry != null ? industry.getNameZh() : null,
                company.getWebsite(), company.getVerified());
    }
}
