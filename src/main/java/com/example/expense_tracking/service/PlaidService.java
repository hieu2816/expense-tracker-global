package com.example.expense_tracking.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.ItemRemoveRequest;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.LinkTokenCreateResponse;
import com.plaid.client.model.Products;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import com.example.expense_tracking.config.PlaidConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaidService {
    private final PlaidApi plaidClient;
    private final PlaidConfig plaidConfig;

    // Create a Plaid Link token for the frontend.
    public String createLinkToken(String clientUserId) {
        try {
            // Attach the current app user to this Link session.
            LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                    .clientUserId(clientUserId);

            // Ask Plaid for auth and transaction access.
            String webhookUrl = plaidConfig.getWebhookUrl();
            LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                    .user(user)
                    .clientName("Expense Tracking Global")
                    .products(List.of(Products.AUTH, Products.TRANSACTIONS))
                    .countryCodes(List.of(CountryCode.GB))
                    .language("en")
                    .webhook(webhookUrl);

            // Send the request and read back the generated token.
            Response<LinkTokenCreateResponse> response = plaidClient.linkTokenCreate(request).execute();
            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "no error body";
                throw new IllegalStateException("Plaid API error: " + response.code() + " - " + errorBody);
            }
            LinkTokenCreateResponse body = response.body();
            if (body == null || body.getLinkToken() == null) {
                throw new IllegalStateException("Plaid returned empty link token");
            }
            return body.getLinkToken();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Plaid link token", e);
        }
    }

    // Exchange the short-lived public token for a permanent access token.
    public ItemPublicTokenExchangeResponse exchangePublicToken(String publicToken) {
        try {
            // Build the exchange request from the public token.
            ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
                    .publicToken(publicToken);
            // Call Plaid and return the item/access token response.
            ItemPublicTokenExchangeResponse response = plaidClient.itemPublicTokenExchange(request).execute().body();
            if (response == null) {
                throw new IllegalStateException("Failed to exchange public token");
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to exchange public token", e);
        }
    }

    // Load every account inside one Plaid item.
    public List<AccountBase> getAccounts(String accessToken) {
        try {
            // Ask Plaid for the accounts under this access token.
            com.plaid.client.model.AccountsGetRequest request = new com.plaid.client.model.AccountsGetRequest()
                    .accessToken(accessToken);
            com.plaid.client.model.AccountsGetResponse response = plaidClient.accountsGet(request).execute().body();
            if (response == null || response.getAccounts() == null) {
                return List.of();
            }
            return response.getAccounts();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch Plaid accounts", e);
        }
    }

    // Pull added, modified, and removed transactions from Plaid.
    public TransactionsSyncResponse syncTransactions(String accessToken, String cursor) {
        try {
            // Use the saved cursor, or null on first sync.
            TransactionsSyncRequest request = new TransactionsSyncRequest()
                    .accessToken(accessToken)
                    .cursor(cursor);
            // Read one page of changes and the next cursor.
            TransactionsSyncResponse response = plaidClient.transactionsSync(request).execute().body();
            if (response == null) {
                throw new IllegalStateException("Failed to sync Plaid transactions");
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to sync Plaid transactions", e);
        }
    }

    // Create a Link token for update mode.
    public String createUpdateLinkToken(String clientUserId, String accessToken) {
        return createLinkToken(clientUserId);
    }

    // Remove the Plaid item from Plaid's side.
    public void removeItem(String accessToken) {
        try {
            // Send the removal request to Plaid.
            plaidClient.itemRemove(new ItemRemoveRequest().accessToken(accessToken)).execute();
        } catch (IOException e) {
            log.warn("Failed to remove Plaid item: {}", e.getMessage());
        }
    }
}
