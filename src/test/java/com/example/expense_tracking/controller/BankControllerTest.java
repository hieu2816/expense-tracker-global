package com.example.expense_tracking.controller;

import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.example.expense_tracking.service.BankLinkingService;
import com.example.expense_tracking.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BankController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for unit test
class BankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BankLinkingService bankLinkingService;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private PlaidItemRepository plaidItemRepository;

    @MockBean
    private com.example.expense_tracking.config.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private org.springframework.security.authentication.AuthenticationProvider authenticationProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Mock a user context if any controller methods check for authentication
        User mockUser = User.builder().id(1L).email("test@example.com").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void handleWebhook_Success() throws Exception {
        String payload = "{\"webhook_type\":\"TRANSACTIONS\",\"webhook_code\":\"DEFAULT_UPDATE\"}";

        mockMvc.perform(post("/api/banks/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Plaid-Verification", "dummy-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));
    }

    @Test
    void handleWebhook_ExceptionThrows_Returns200ErrorStatus() throws Exception {
        String payload = "{\"webhook_type\":\"TRANSACTIONS\"}";

        // Force the service to throw an exception to test the catch block
        doThrow(new RuntimeException("Simulated webhook failure"))
                .when(webhookService).handleWebhook(any(), any());

        // We expect an HTTP 200 (OK) even on failure, to prevent Plaid from infinitely retrying.
        mockMvc.perform(post("/api/banks/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"));
    }
}