package com.Jolie.career_toolkit.company.dto;

import com.Jolie.career_toolkit.company.Industry;

public record IndustryResponse(Short id, String code, String name) {

    public static IndustryResponse from(Industry industry) {
        return new IndustryResponse(industry.getId(), industry.getCode(), industry.getNameZh());
    }
}
