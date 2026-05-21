package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.BankLinkRequest;
import com.example.expense_tracking.dto.BankLinkResponse;
import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ForbiddenException;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.BankAccountRepository;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankLinkingServiceTest {

    @Mock
    private PlaidService plaidService;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private PlaidItemRepository plaidItemRepository;
    @Mock
    private TransactionSyncService transactionSyncService;

    @InjectMocks
    private BankLinkingService bankLinkingService;

    private User testUser;
    private PlaidItem testItem;
    private BankAccount testAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@example.com").build();
        testItem = PlaidItem.builder().id(1L).itemId("item-1").accessToken("access-1").user(testUser).status("ACTIVE").build();
        testAccount = BankAccount.builder().id(1L).plaidAccountId("acc-1").plaidItem(testItem).user(testUser).build();
    }

    @Test
    void createLinkToken_Success() {
        when(plaidService.createLinkToken(anyString())).thenReturn("link-token-123");
        String token = bankLinkingService.createLinkToken(testUser);
        assertEquals("link-token-123", token);
    }

    @Test
    void completeLinking_Success() {
        BankLinkRequest request = new BankLinkRequest();
        request.setPublicToken("public-123");
        request.setInstitutionId("ins-1");
        request.setInstitutionName("Test Bank");

        ItemPublicTokenExchangeResponse exchangeResponse = new ItemPublicTokenExchangeResponse()
                .accessToken("access-123")
                .itemId("item-123");
        when(plaidService.exchangePublicToken("public-123")).thenReturn(exchangeResponse);

        AccountBase accBase = new AccountBase().accountId("plaid-acc-1").name("Checking").mask("1234").type(com.plaid.client.model.AccountType.DEPOSITORY);
        AccountsGetResponse accResponse = new AccountsGetResponse().accounts(List.of(accBase));
        when(plaidService.getAccounts("access-123")).thenReturn(accResponse);

        when(plaidItemRepository.save(any(PlaidItem.class))).thenAnswer(i -> i.getArgument(0));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionSyncService.initialSync(any())).thenReturn(5);

        BankLinkResponse response = bankLinkingService.completeLinking(request, testUser);

        assertNotNull(response);
        assertEquals("ACTIVE", response.getStatus());
        verify(plaidItemRepository).save(any(PlaidItem.class));
        verify(bankAccountRepository).save(any(BankAccount.class));
        verify(transactionSyncService).initialSync(any());
    }

    @Test
    void getLinkedBanks_Success() {
        when(bankAccountRepository.findByUser(testUser)).thenReturn(List.of(testAccount));
        List<BankLinkResponse> responses = bankLinkingService.getLinkedBanks(testUser);
        assertEquals(1, responses.size());
    }

    @Test
    void unlinkBank_Success() {
        when(bankAccountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(bankAccountRepository.findByPlaidItem_Id(testItem.getId())).thenReturn(List.of(testAccount));

        bankLinkingService.unlinkBank(1L, testUser);

        verify(bankAccountRepository).save(testAccount);
        verify(plaidItemRepository).delete(testItem);
    }

    @Test
    void unlinkBank_Forbidden() {
        User otherUser = User.builder().id(99L).build();
        testAccount.setUser(otherUser);
        when(bankAccountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(ForbiddenException.class, () -> bankLinkingService.unlinkBank(1L, testUser));
    }
}