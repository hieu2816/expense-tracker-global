package com.example.expense_tracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CassoWebhookDTO {
    private int error;
    private CassoTransaction data;

    @Data
    public static class CassoTransaction {
        private Long id; // Casso's internal ID

        @JsonProperty("reference")
        private String tid; // Bank Transaction ID
        private String description;
        private BigDecimal amount;

        @JsonProperty("accountNumber")
        private String accountNumber;

        @JsonProperty("transactionDateTime")
        private String transactionDate;
    }
}
