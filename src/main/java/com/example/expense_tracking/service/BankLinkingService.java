package com.example.expense_tracking.service;

import com.example.expense_tracking.config.SyncConfig;
import com.example.expense_tracking.dto.SyncLogResponse;
import com.example.expense_tracking.dto.bank.BankAccountResponse;
import com.example.expense_tracking.dto.bank.LinkBankResponse;
import com.example.expense_tracking.dto.bank.PlaidExchangeRequest;
import com.example.expense_tracking.dto.bank.SyncRequest;
import com.example.expense_tracking.dto.bank.SyncResponse;
import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.SyncLog;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.BadRequestException;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.BankAccountRepository;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.example.expense_tracking.repository.SyncLogRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BankLinkingService {
    private final PlaidService plaidService;
    private final TransactionSyncService transactionSyncService;
    private final BankAccountRepository bankConfigRepository;
    private final PlaidItemRepository plaidItemRepository;
    private final TransactionRepository transactionRepository;
    private final SyncLogRepository syncLogRepository;
    private final SyncConfig syncConfig;

    // Return all bank accounts owned by the current user.
    @Transactional(readOnly = true)
    public List<BankAccountResponse> getUserBankAccounts(User user) {
        return bankConfigRepository.findByUser(user).stream().map(this::mapToBankAccountResponse).toList();
    }

    // Return one bank account if the user owns it.
    @Transactional(readOnly = true)
    public BankAccountResponse getUserBankAccount(User user, Long bankAccountId) {
        return bankConfigRepository.findByIdAndUser(bankAccountId, user)
                .map(this::mapToBankAccountResponse)
                .orElse(null);
    }

    // Trigger a manual sync for one linked account.
    @Transactional
    public SyncResponse manualSync(User user, Long bankAccountId, SyncRequest syncRequest) {
        // Load the account and verify ownership.
        BankAccount bankAccount = bankConfigRepository.findByIdAndUser(bankAccountId, user)
                .orElse(null);
        if (bankAccount == null) {
            return SyncResponse.builder().status("FAILED").errorMessage("Bank account not found").build();
        }

        // Sync at the Plaid item level because one item can have many accounts.
        PlaidItem item = bankAccount.getPlaidItem();
        if (item == null) {
            return SyncResponse.builder().status("FAILED").errorMessage("Plaid item not found").build();
        }

        try {
            // Delegate to the item sync engine and return the result count.
            TransactionSyncService.SyncResult synced = transactionSyncService.syncItem(item.getItemId()).join();
            return SyncResponse.builder()
                    .status("SUCCESS")
                    .transactionsFetched(synced.totalAdded())
                    .transactionsNew(synced.totalAdded())
                    .build();
        } catch (Exception e) {
            return SyncResponse.builder().status("FAILED").errorMessage("Sync failed: " + e.getMessage()).build();
        }
    }

    // Unlink one bank account and remove the item if it is no longer used.
    @Transactional
    public boolean unlinkBank(User user, Long bankAccountId) {
        // Load and verify ownership first.
        BankAccount bankAccount = bankConfigRepository.findByIdAndUser(bankAccountId, user)
                .orElse(null);
        if (bankAccount == null) {
            return false;
        }

        // Detach transactions so history is preserved.
        transactionRepository.unlinkTransactionsFromBankAccount(bankAccount);
        bankConfigRepository.deleteById(bankAccountId);

        // Remove the Plaid item if no local accounts still use it.
        PlaidItem item = bankAccount.getPlaidItem();
        if (item != null && bankConfigRepository.findByPlaidItem_Id(item.getId()).isEmpty()) {
            plaidService.removeItem(item.getAccessToken());
            plaidItemRepository.delete(item);
        }

        return true;
    }

    // Return sync history for one linked bank account.
    public Page<SyncLogResponse> getSyncHistory(User user, Long bankAccountId, int page, int size) {
        // Confirm ownership before exposing history.
        BankAccount bankAccount = bankConfigRepository.findByIdAndUser(bankAccountId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
        PlaidItem item = bankAccount.getPlaidItem();
        if (item == null) {
            throw new ResourceNotFoundException("Plaid item not found");
        }

        // Read history from the item because sync runs at item level.
        return syncLogRepository.findByPlaidItemOrderBySyncedAtDesc(item, PageRequest.of(page, size))
                .map(this::mapToSyncLogResponse);
    }

    // Start the Plaid Link flow and return a Link token.
    @Transactional
    public LinkBankResponse startLinking(User user, String institutionId, String countryCode) {
        // Create the token used by the frontend Plaid widget.
        String linkToken = plaidService.createLinkToken(String.valueOf(user.getId()));
        return LinkBankResponse.builder()
                .linkToken(linkToken)
                .link(linkToken)
                .institutionName(null)
                .build();
    }

    // Finish the Plaid Link flow after the frontend gets a public token.
    @Transactional
    public void completeLinking(User user, PlaidExchangeRequest request) {
        var exchange = plaidService.exchangePublicToken(request.getPublicToken());
        var accessToken = exchange.getAccessToken();
        var itemId = exchange.getItemId();

        PlaidItem item = plaidItemRepository.findByItemId(itemId)
                .orElseGet(() -> PlaidItem.builder()
                        .user(user)
                        .itemId(itemId)
                        .accessToken(accessToken)
                        .status("ACTIVE")
                        .build());

        item.setAccessToken(accessToken);
        item.setStatus("ACTIVE");
        plaidItemRepository.save(item);

        var accounts = plaidService.getAccounts(accessToken);
        for (var account : accounts) {
            BankAccount bankAccount = bankConfigRepository.findByPlaidAccountId(account.getAccountId())
                    .orElseGet(BankAccount::new);
            bankAccount.setUser(user);
            bankAccount.setPlaidItem(item);
            bankAccount.setPlaidAccountId(account.getAccountId());
            bankAccount.setName(account.getName());
            bankAccount.setOfficialName(account.getOfficialName());
            bankAccount.setMask(account.getMask());
            bankAccount.setSubtype(account.getSubtype() != null ? account.getSubtype().toString() : null);
            bankAccount.setStatus("ACTIVE");
            bankConfigRepository.save(bankAccount);
        }

        if (!accounts.isEmpty()) {
            transactionSyncService.initialSync(bankConfigRepository.findByPlaidAccountId(accounts.get(0).getAccountId()).orElse(null));
        }
    }

    // Map a bank account entity into the response DTO.
    private BankAccountResponse mapToBankAccountResponse(BankAccount config) {
        return BankAccountResponse.builder()
                .id(config.getId())
                .institutionId(config.getPlaidItem() != null ? config.getPlaidItem().getInstitutionId() : null)
                .institutionName(config.getPlaidItem() != null ? config.getPlaidItem().getInstitutionName() : null)
                .institutionLogo(config.getPlaidItem() != null ? config.getPlaidItem().getInstitutionLogo() : null)
                .maskedIban(config.getMask())
                .accountName(config.getName())
                .status(config.getStatus())
                .lastSyncedAt(config.getLastSyncedAt())
                .accessExpiresAt(null)
                .createdAt(config.getCreatedAt())
                .build();
    }

    // Map a sync log entity into the response DTO.
    private SyncLogResponse mapToSyncLogResponse(SyncLog log) {
        return SyncLogResponse.builder()
                .id(log.getId())
                .syncedAt(log.getSyncedAt())
                .transactionsFetched(log.getTransactionsFetched())
                .transactionsNew(log.getTransactionsNew())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
