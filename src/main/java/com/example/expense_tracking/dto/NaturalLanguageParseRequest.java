package com.example.expense_tracking.dto;

import lombok.Data;

@Data
public class NaturalLanguageParseRequest {
    @jakarta.validation.constraints.NotBlank(message = "Text is required")
    private String text;

    private String language;
}
