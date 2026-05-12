package com.example.expense_tracking.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PlaidWebhookRequest {
    @JsonProperty("webhook_type")
    private String webhookType;

    @JsonProperty("webhook_code")
    private String webhookCode;

    @JsonProperty("item_id")
    private String itemId;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("new_access_token")
    private String newAccessToken;

    @JsonProperty("new_item_id")
    private String newItemId;

    @JsonProperty("removed_transaction_ids")
    private List<String> removedTransactionIds;

    @JsonProperty("initial_update_complete")
    private Boolean initialUpdateComplete;

    @JsonProperty("historical_update_complete")
    private Boolean historicalUpdateComplete;

    @JsonProperty("user_permission_revoked")
    private Boolean userPermissionRevoked;

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("request_id")
    private String requestId;
}