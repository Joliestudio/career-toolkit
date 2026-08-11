package com.Jolie.career_toolkit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,

        // 長度下限比複雜度規則有效得多。強制大小寫加符號只會讓人用 Password1!，
        // 那對字典攻擊幾乎沒有幫助。
        @NotBlank @Size(min = 8, max = 100) String password,

        @Size(max = 100) String displayName
) {}
