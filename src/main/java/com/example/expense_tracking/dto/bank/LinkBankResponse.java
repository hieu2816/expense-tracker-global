package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Response for starting the Plaid Link flow.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkBankResponse {
    // Plaid link token used by the frontend.
    private String linkToken;

    // Optional redirect/link value returned to the UI.
    private String link;

    // Display name for the institution.
    private String institutionName;
}
