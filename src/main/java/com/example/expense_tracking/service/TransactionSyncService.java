package com.example.expense_tracking.service;

import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.repository.BankAccountRepository;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.example.expense_tracking.repository.SyncLogRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionSyncService {
    private final PlaidService plaidService;
    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PlaidItemRepository plaidItemRepository;
    private final SyncLogRepository syncLogRepository;

    // Sync every active Plaid item.
    @Transactional
    public void syncAllActiveAccounts() {
        // Load only active items so we skip removed or broken connections.
        List<PlaidItem> activeItems = plaidItemRepository.findByStatus("ACTIVE");
        for (PlaidItem item : activeItems) {
            try {
                // Sync at item level because one item can have many accounts.
                syncItem(item.getItemId());
            } catch (Exception e) {
                log.error("Failed to sync Plaid item {}: {}", item.getItemId(), e.getMessage(), e);
            }
        }
    }

    // Run the first sync for one linked account.
    @Transactional
    public int initialSync(BankAccount bankAccount) {
        // Use the owning Plaid item, not the account row.
        PlaidItem item = bankAccount.getPlaidItem();
        if (item == null) {
            return 0;
        }
        return syncItem(item.getItemId()).join().totalAdded();
    }

    // Keep the old signature but sync the owning Plaid item.
    @Transactional
    public int syncTransactions(BankAccount bankAccount, LocalDate dateFrom, LocalDate dateTo) {
        // Dates are ignored because Plaid cursor sync controls the data range.
        PlaidItem item = bankAccount.getPlaidItem();
        if (item == null) {
            return 0;
        }
        return syncItem(item.getItemId()).join().totalAdded();
    }

    // Pull new, changed, and removed transactions for one item.
    // Marked @Async so it runs in background when called from WebhookService.
    // Accepts itemId String to avoid detached entity issues in @Async context.
    @Async
    @Transactional
    public CompletableFuture<SyncResult> syncItem(String itemId) {
        // Query PlaidItem inside the method to attach it to current session (fixes detached entity issue).
        PlaidItem item = plaidItemRepository.findByItemId(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Plaid item not found: " + itemId));

        // Start from the last saved cursor so we fetch only deltas.
        String cursor = item.getSyncCursor();
        // Build a fast lookup from Plaid account id to local account row.
        List<BankAccount> accounts = bankAccountRepository.findByPlaidItem_Id(item.getId());
        Map<String, BankAccount> accountMap = new HashMap<>();
        for (BankAccount account : accounts) {
            accountMap.put(account.getPlaidAccountId(), account);
        }

        int totalAdded = 0;
        int totalModified = 0;
        int totalRemoved = 0;
        boolean hasMore = true;

        while (hasMore) {
            // Ask Plaid for the next page of changes.
            var response = plaidService.syncTransactions(item.getAccessToken(), cursor);

            if (response.getRemoved() != null) {
                for (var removed : response.getRemoved()) {
                    // Remove means the transaction disappeared, so soft-delete it locally.
                    BankAccount account = accountMap.get(removed.getAccountId());
                    if (account == null) continue;
                    // Direct DB query replaces O(N) memory load with O(1) index lookup for performance.
                    Transaction tx = transactionRepository.findByPlaidTransactionIdAndBankAccount(removed.getTransactionId(), account);
                    if (tx != null) {
                        tx.setIsDeleted(true);
                        tx.setDeletedAt(LocalDateTime.now());
                        transactionRepository.save(tx);
                    }
                    totalRemoved++;
                }
            }

            if (response.getModified() != null) {
                for (var modified : response.getModified()) {
                    // Modified means the transaction still exists but details changed.
                    BankAccount account = accountMap.get(modified.getAccountId());
                    if (account == null) continue;
                    // Direct DB query replaces O(N) memory load with O(1) index lookup for performance.
                    Transaction tx = transactionRepository.findByPlaidTransactionIdAndBankAccount(modified.getTransactionId(), account);
                    if (tx != null) {
                        tx.setAmount(BigDecimal.valueOf(modified.getAmount()));
                        tx.setDescription(modified.getName());
                        tx.setIsDeleted(false);
                        tx.setDeletedAt(null);
                        transactionRepository.save(tx);
                    }
                    totalModified++;
                }
            }

            if (response.getAdded() != null) {
                for (var added : response.getAdded()) {
                    // Added means the transaction is new and should be inserted.
                    BankAccount account = accountMap.get(added.getAccountId());
                    if (account == null) continue;

                    Transaction tx = Transaction.builder()
                            .user(item.getUser())
                            .bankAccount(account)
                            .plaidTransactionId(added.getTransactionId())
                            .amount(BigDecimal.valueOf(Math.abs(added.getAmount())))
                            .type(added.getAmount() < 0 ? TransactionType.OUT : TransactionType.IN)
                            .currency(added.getIsoCurrencyCode())
                            .description(added.getName())
                            .transactionDate(added.getDate().atStartOfDay())
                            .isDeleted(false)
                            .build();
                    try {
                        // The unique constraint is the duplicate guard.
                        transactionRepository.saveAndFlush(tx);
                        totalAdded++;
                    } catch (DataIntegrityViolationException e) {
                        log.trace("Duplicate transaction skipped: {}", added.getTransactionId());
                    }
                }
            }

            // Keep paging until Plaid says there are no more changes.
            hasMore = Boolean.TRUE.equals(response.getHasMore());
            cursor = response.getNextCursor();
        }

        // Save the new cursor only after the full sync succeeds.
        item.setSyncCursor(cursor);
        item.setUpdatedAt(LocalDateTime.now());
        plaidItemRepository.save(item);

        // Update timestamps on all linked accounts.
        accounts.forEach(account -> account.setLastSyncedAt(LocalDateTime.now()));
        bankAccountRepository.saveAll(accounts);

        return CompletableFuture.completedFuture(new SyncResult(totalAdded, totalModified, totalRemoved));
    }

    public record SyncResult(int totalAdded, int totalModified, int totalRemoved) {}
}
