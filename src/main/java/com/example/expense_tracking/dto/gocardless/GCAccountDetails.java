package com.example.expense_tracking.dto.gocardless;

import lombok.Data;

// Contain details about a bank account linked vid GoCardless
// Maps the JSON from GET /api/v2/accounts/{id}/details/
@Data
public class GCAccountDetails {
    private String resourceId;
    private String iban;
    private String currency;
    private String ownerName;
    private String name;
    private String product;
    private String cashAccountType;
    private AdditionalAccountData additionalAccountData;

    @Data
    public static class AdditionalAccountData {
        private String secondaryIdentification;
    }
}
