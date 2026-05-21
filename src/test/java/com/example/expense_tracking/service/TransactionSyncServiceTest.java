package com.example.expense_tracking.service;

import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.BankAccountRepository;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import com.plaid.client.model.TransactionModified;
import com.plaid.client.model.RemovedTransaction;
import com.plaid.client.model.TransactionsSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSyncServiceTest {

    @Mock
    private PlaidService plaidService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private PlaidItemRepository plaidItemRepository;

    @InjectMocks
    private TransactionSyncService transactionSyncService;

    private User testUser;
    private PlaidItem testItem;
    private BankAccount testAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@example.com").build();
        testItem = PlaidItem.builder().id(1L).itemId("item-1").accessToken("access-1").user(testUser).syncCursor("cursor-1").build();
        testAccount = BankAccount.builder().id(1L).plaidAccountId("acc-1").plaidItem(testItem).user(testUser).build();
    }

    @Test
    void syncAllActiveAccounts_Success() {
        when(plaidItemRepository.findByStatus("ACTIVE")).thenReturn(List.of(testItem));
        when(plaidItemRepository.findByItemId("item-1")).thenReturn(Optional.of(testItem));
        when(bankAccountRepository.findByPlaidItem_Id(1L)).thenReturn(List.of(testAccount));

        TransactionsSyncResponse mockResponse = new TransactionsSyncResponse()
                .hasMore(false)
                .nextCursor("cursor-2")
                .added(Collections.emptyList())
                .modified(Collections.emptyList())
                .removed(Collections.emptyList());
        when(plaidService.syncTransactions(anyString(), anyString())).thenReturn(mockResponse);

        assertDoesNotThrow(() -> transactionSyncService.syncAllActiveAccounts());
        verify(plaidItemRepository).save(any(PlaidItem.class));
    }

    @Test
    void syncItem_HandleAdded_Duplicate() {
        when(plaidItemRepository.findByItemId("item-1")).thenReturn(Optional.of(testItem));
        when(bankAccountRepository.findByPlaidItem_Id(1L)).thenReturn(List.of(testAccount));

        com.plaid.client.model.Transaction addedTx = new com.plaid.client.model.Transaction()
                .accountId("acc-1")
                .transactionId("tx-1")
                .amount(100.0)
                .name("Test Tx")
                .date(LocalDate.now());

        TransactionsSyncResponse mockResponse = new TransactionsSyncResponse()
                .hasMore(false)
                .nextCursor("cursor-2")
                .added(List.of(addedTx))
                .modified(Collections.emptyList())
                .removed(Collections.emptyList());

        when(plaidService.syncTransactions(anyString(), anyString())).thenReturn(mockResponse);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenThrow(new DataIntegrityViolationException("Duplicate"));

        CompletableFuture<TransactionSyncService.SyncResult> future = transactionSyncService.syncItem("item-1");
        TransactionSyncService.SyncResult result = future.join();

        assertNotNull(result);
        assertEquals(0, result.totalAdded()); // Because it threw Duplicate exception, added count doesn't increment
    }

    @Test
    void syncItem_HandleModifiedAndRemoved() {
        when(plaidItemRepository.findByItemId("item-1")).thenReturn(Optional.of(testItem));
        when(bankAccountRepository.findByPlaidItem_Id(1L)).thenReturn(List.of(testAccount));

        Transaction existingTx = Transaction.builder().id(1L).plaidTransactionId("tx-mod").build();

        TransactionModified modTx = new TransactionModified().accountId("acc-1").transactionId("tx-mod").amount(50.0).name("Mod Tx");
        RemovedTransaction remTx = new RemovedTransaction().accountId("acc-1").transactionId("tx-rem");

        TransactionsSyncResponse mockResponse = new TransactionsSyncResponse()
                .hasMore(false)
                .nextCursor("cursor-2")
                .added(Collections.emptyList())
                .modified(List.of(modTx))
                .removed(List.of(remTx));

        when(plaidService.syncTransactions(anyString(), anyString())).thenReturn(mockResponse);
        when(transactionRepository.findByPlaidTransactionIdAndBankAccount("tx-mod", testAccount)).thenReturn(existingTx);
        when(transactionRepository.findByPlaidTransactionIdAndBankAccount("tx-rem", testAccount)).thenReturn(Transaction.builder().id(2L).plaidTransactionId("tx-rem").build());

        CompletableFuture<TransactionSyncService.SyncResult> future = transactionSyncService.syncItem("item-1");
        TransactionSyncService.SyncResult result = future.join();

        assertEquals(1, result.totalModified());
        assertEquals(1, result.totalRemoved());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }
}