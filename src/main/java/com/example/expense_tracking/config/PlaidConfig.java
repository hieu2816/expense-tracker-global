package com.example.expense_tracking.config;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
@Data
public class PlaidConfig {
    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.environment}")
    private String environment;

    @Value("${plaid.webhook-url:}")
    private String webhookUrl;

    @Bean
    public PlaidApi plaidClient() {
        System.out.println("PLAID CONFIG: clientId=" + clientId + ", secret=" + secret + ", env=" + environment + ", webhookUrl=" + webhookUrl);
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", clientId);
        apiKeys.put("secret", secret);
        apiKeys.put("plaidVersion", "2020-09-14");

        ApiClient apiClient = new ApiClient(apiKeys);
        if ("production".equalsIgnoreCase(environment)) {
            apiClient.setPlaidAdapter(ApiClient.Production);
        } else {
            apiClient.setPlaidAdapter(ApiClient.Sandbox);
        }
        return apiClient.createService(PlaidApi.class);
    }
}
