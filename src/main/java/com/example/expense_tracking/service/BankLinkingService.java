package com.example.expense_tracking.service;

import com.example.expense_tracking.config.SyncConfig;
import com.example.expense_tracking.dto.bank.*;
import com.example.expense_tracking.dto.gocardless.GCAccountDetails;
import com.example.expense_tracking.dto.gocardless.GCInstitution;
import com.example.expense_tracking.dto.gocardless.GCRequisitionResponse;
import com.example.expense_tracking.entity.BankConfig;
import com.example.expense_tracking.entity.SyncLog;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.BankConfigRepository;
import com.example.expense_tracking.repository.SyncLogRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

// Service for managing bank account linking flow
// Uses GoCardlessService for API calls and TransactionSyncService for syncing
@Service
@Slf4j
@RequiredArgsConstructor
public class BankLinkingService {
    private final GoCardlessService goCardlessService;
    private final TransactionSyncService transactionSyncService;
    private final BankConfigRepository bankConfigRepository;
    private final TransactionRepository transactionRepository;
    private final SyncLogRepository syncLogRepository;
    private final SyncConfig syncConfig;

    // BankConfig status constants
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_LINKED = "LINKED";
    private static final String STATUS_ERROR = "ERROR";

    // Get list of available banks/institutions for a country
    public List<GCInstitution> getInstitutions(String countryCode) {
        log.info("Fetching institutions for country: {}", countryCode);
        return goCardlessService.getInstitutions(countryCode);
    }

    // Start the bank linking process
    @Transactional
    public LinkBankResponse startLinking(User user, String institutionId, String countryCode) {
        log.info("Starting bank linking for user {} with institution {}", user.getEmail(), institutionId);
        // 1. Generate unique reference (user_id + timestamp)
        String reference = "user_" + user.getId() + "_" + System.currentTimeMillis();

        // 2. Create requisition via GoCardless
        GCRequisitionResponse requisition = goCardlessService.createRequisition(institutionId, reference);

        if (requisition == null || requisition.getLink() == null) {
            log.error("Failed to create requisition for user {}", user.getEmail());
            throw new RuntimeException("Failed to create bank linking session");
        }

        // 3. Get institution details for name and logo
        String institutionName = null;
        String institutionLogo = null;

        List<GCInstitution> institutions = goCardlessService.getInstitutions(countryCode != null ? countryCode : "GB");
        for (GCInstitution institution : institutions) {
            if (institution.getId().equals(institutionId)) {
                institutionName = institution.getName();
                institutionLogo = institution.getLogo();
                break;
            }
        }

        // 4. Save BankConfig with status = PENDING
        BankConfig bankConfig = new BankConfig();
        bankConfig.setUser(user);
        bankConfig.setInstitutionId(institutionId);
        bankConfig.setInstitutionName(institutionName);
        bankConfig.setInstitutionLogo(institutionLogo);
        bankConfig.setRequisitionId(requisition.getId());
        bankConfig.setStatus(STATUS_PENDING);

        bankConfigRepository.save(bankConfig);

        log.info("Created bank config {} with requisition {}", bankConfig.getId(), requisition.getId());

        // 5. Return LinkBankResponse with requisition ID and link
        return LinkBankResponse.builder()
                .requisitionId(requisition.getId())
                .link(requisition.getLink())
                .institutionName(institutionName)
                .build();
    }

    // Process callback after user authorizes bank access
    @Transactional
    public CallbackResponse processCallback(String reference) {
        log.info("Process callback with reference: {}", reference);

        // 1. Extract user ID from reference (reference format: user_{userId}_{timestamp})
        try {
            String[] parts = reference.split("_");
            if (parts.length < 3 || !parts[0].equals("user")) {
                log.error("Invalid reference format: {}", reference);
                return buildErrorResponse("Invalid callback reference format");
            }

            Long userId = Long.parseLong(parts[1]);

            // 2. Find BankConfig by reference pattern (user's pending requisition)
            List<BankConfig> pendingConfigs = bankConfigRepository.findByStatus(STATUS_PENDING);
            BankConfig bankConfig = null;

            for (BankConfig config : pendingConfigs) {
                if (config.getUser().getId().equals(userId) && config.getRequisitionId() != null) {
                    // Verify this requisition by checking with GoCardless
                    GCRequisitionResponse requisition = goCardlessService.getRequisition(config.getRequisitionId());
                    if (requisition != null && reference.equals(requisition.getReference())) {
                        bankConfig = config;
                        break;
                    }
                }
            }

            if (bankConfig == null) {
                log.error("No pending bank config found for reference: {}", reference);
                return buildErrorResponse("No pending bank linking session found");
            }

            // 3. Fetch requisition from GoCardless
            GCRequisitionResponse requisition = goCardlessService.getRequisition(bankConfig.getRequisitionId());

            if (requisition == null) {
                log.error("failed to fetch requisition: {}", bankConfig.getRequisitionId());
                return buildErrorResponse("Failed to verified bank authorization");
            }

            // 4. Check if status is "LN" (LINKED)
            if (!"LN".equals(requisition.getStatus())) {
                log.warn("Requisition {} has status {} (expected LN)", requisition.getId(), requisition.getStatus());

                // Update bank config status based on requisition status
                if ("RJ".equals(requisition.getStatus())) {
                    bankConfig.setStatus(STATUS_ERROR);
                    bankConfigRepository.save(bankConfig);
                    return buildErrorResponse("Bank authorization was rejected");
                } else if ("EX".equals(requisition.getStatus())) {
                    bankConfig.setStatus(STATUS_ERROR);
                    bankConfigRepository.save(bankConfig);
                    return buildErrorResponse("Bank authorization has expired");
                } else {
                    return buildErrorResponse("Bank authorization is still pending. Status: " + requisition.getStatus());
                }
            }

            // 5. Process each account in the requisition
            List<String> accountIds = requisition.getAccounts();
            if (accountIds == null || accountIds.isEmpty()) {
                log.error("No accounts found in requisition: {}", requisition.getId());
                bankConfig.setStatus(STATUS_ERROR);
                bankConfigRepository.save(bankConfig);
                return buildErrorResponse("No bank accounts found");
            }

            List<BankAccountResponse> linkedAccounts = new ArrayList<>();
            int totalTransactionsSynced = 0;

            for (int i = 0; i < accountIds.size(); i++) {
                String accountId = accountIds.get(i);

                // 5a. Fetch account details from GoCardless
                GCAccountDetails accountDetails = goCardlessService.getAccountDetails(accountId);

                // For first account, update the existing BankConfig
                // For additional accounts, create new BankConfig entries
                BankConfig configToUpdate;
                if (i == 0) {
                    configToUpdate = bankConfig;
                } else {
                    configToUpdate = new BankConfig();
                    configToUpdate.setUser(bankConfig.getUser());
                    configToUpdate.setInstitutionId(bankConfig.getInstitutionId());
                    configToUpdate.setInstitutionName(bankConfig.getInstitutionName());
                    configToUpdate.setInstitutionLogo(bankConfig.getInstitutionLogo());
                    configToUpdate.setRequisitionId(bankConfig.getRequisitionId());
                }

                // 5b. Update BankConfig with account info
                configToUpdate.setGocardlessAccountId(accountId);
                if (accountDetails != null) {
                    configToUpdate.setIban(accountDetails.getIban());
                    configToUpdate.setAccountName(accountDetails.getOwnerName());
                }

                // 5c. Set status = LINKED and calculate access expiry
                configToUpdate.setStatus(STATUS_LINKED);
                // Default access validity is 90 days, can be adjusted based in institution
                configToUpdate.setAccessExpiresAt(LocalDateTime.now().plusDays(90));

                bankConfigRepository.save(configToUpdate);

                // 5c. Trigger initial sync
                try {
                    int synced = transactionSyncService.initialSync(configToUpdate);
                    totalTransactionsSynced += synced;
                    log.info("Initial sync for account{}: {} transactions", accountId, synced);
                } catch (Exception e) {
                    log.error("Initial sync failed for account {}: {}", accountId, e.getMessage());
                    // Continue with other accounts even if sync fails
                }

                // Add to response list
                linkedAccounts.add(mapToBankAccountResponse(configToUpdate));
            }

            // 6. Return success response
            log.info("Successfully linked {} accounts for user, synced {} transactions", linkedAccounts.size(), totalTransactionsSynced);

            return CallbackResponse.builder()
                    .status("SUCCESS")
                    .message("Bank account linked successfully")
                    .bankAccounts(linkedAccounts)
                    .transactionsSynced(totalTransactionsSynced)
                    .build();
        } catch (Exception e) {
            log.error("Error processing callback: {}", e.getMessage(), e);
            return buildErrorResponse("Failed to process bank authorization: " + e.getMessage());
        }
    }

    // Get all bank account for a user
    public List<BankAccountResponse> getUserBankAccounts(User user) {
        List<BankConfig> configs = bankConfigRepository.findByUser(user);
        return configs.stream().map(this::mapToBankAccountResponse).toList();
    }

    // Get a specific bank account for a user
    public BankAccountResponse getUserBankAccount(User user, Long bankConfigId) {
        return bankConfigRepository.findByIdAndUser(bankConfigId, user)
                .map(this::mapToBankAccountResponse)
                .orElse(null);
    }

    // Manually trigger sync for a bank account
    @Transactional
    public SyncResponse manualSync (User user, Long bankConfigId, SyncRequest syncRequest) {
        log.info("Manual sync requested for bank config {} by user {}", bankConfigId, user.getEmail());

        // 1. Find BankConfig and verify ownership
        BankConfig bankConfig = bankConfigRepository.findByIdAndUser(bankConfigId, user)
                .orElse(null);

        if (bankConfig == null) {
            return SyncResponse.builder()
                    .status("FAILED")
                    .errorMessage("Bank account not found")
                    .build();
        }

        // 2. Check status is LINKED
        if (!STATUS_LINKED.equals(bankConfig.getStatus())) {
            return SyncResponse.builder()
                    .status("FAILED")
                    .errorMessage("Bank account is not linked. Current status: " + bankConfig.getStatus())
                    .build();
        }

        // 3. Check access hasn't expired
        if (bankConfig.getAccessExpiresAt() != null && bankConfig.getAccessExpiresAt().isBefore(LocalDateTime.now())) {
            return SyncResponse.builder()
                    .status("FAILED")
                    .errorMessage("Bank access has expired on " + bankConfig.getAccessExpiresAt() + ". Please relink your bank account.")
                    .build();
        }

        // 4. Determine date range
        LocalDate dateTo = (syncRequest != null && syncRequest.getDateTo() != null)
                ? syncRequest.getDateTo()
                : LocalDate.now();
        LocalDate dateFrom = (syncRequest != null && syncRequest.getDateFrom() != null)
                ? syncRequest.getDateFrom()
                : dateTo.minusDays(syncConfig.getLookBackDays());

        // 5. Call sync service
        try {
            int newTransactions = transactionSyncService.syncTransactions(bankConfig, dateFrom, dateTo);

            // 6. Return success response
            return SyncResponse.builder()
                    .status("SUCCESS")
                    .dateFrom(dateFrom)
                    .dateTo(dateTo)
                    .transactionsFetched(newTransactions)
                    .transactionsNew(newTransactions)
                    .build();
        } catch (Exception e) {
            log.error("Manual sync failed for bank config {}: {}", bankConfigId, e.getMessage());
            return SyncResponse.builder()
                    .status("FAILED")
                    .dateFrom(dateFrom)
                    .dateTo(dateTo)
                    .errorMessage("Sync failed: " + e.getMessage())
                    .build();
        }
    }

    // Unlink a bank account
    @Transactional
    public boolean unlinkBank(User user, Long bankConfigId) {
        log.info("Unlinking bank config {} for user {}", bankConfigId, user.getEmail());

        // 1. Find BankConfig and verify ownership
        BankConfig bankConfig = bankConfigRepository.findByIdAndUser(bankConfigId, user)
                .orElse(null);

        if (bankConfig == null) {
            log.warn("Bank config {} not found for user {}", bankConfigId, user.getEmail());
            return false;
        }

        // 2. Unlink all transactions (set bankConfig = null
        transactionRepository.unlinkTransactionsFromBankConfig(bankConfig);
        log.info("Unlinked transactions from bank config {}", bankConfigId);

        // 3. Delete the BankConfig
        bankConfigRepository.deleteById(bankConfigId);
        log.info("Deleted bank config {}", bankConfigId);

        return true;
    }

    // Get sync history for a bank account
    public Page<SyncLog> getSyncHistory(User user, Long bankConfigId, int page, int size) {
        BankConfig bankConfig = bankConfigRepository.findByIdAndUser(bankConfigId, user)
                .orElseThrow(() -> new RuntimeException("Bank account not found"));

        return syncLogRepository.findByBankConfigOrderBySyncedAtDesc(
                bankConfig, PageRequest.of(page, size));
    }

    // ==================== PRIVATE HELPER METHODS ====================

    // Map BankConfig entity to BankAccountResponse DTO
    private BankAccountResponse mapToBankAccountResponse(BankConfig config) {
        return BankAccountResponse.builder()
                .id(config.getId())
                .institutionId(config.getInstitutionId())
                .institutionName(config.getInstitutionName())
                .institutionLogo(config.getInstitutionLogo())
                .maskedIban(maskIban(config.getIban()))
                .accountName(config.getAccountName())
                .status(config.getStatus())
                .lastSyncedAt(config.getLastSyncedAt())
                .accessExpiresAt(config.getAccessExpiresAt())
                .createdAt(config.getCreatedAt())
                .build();
    }

    // Mask IBAN for security - shows only last 4 characters.
    // * Example: "GB29NWBK60161331926819" → "GB29****6819"
    private String maskIban(String iban) {
        if (iban == null || iban.length() <= 8) {
            return iban;
        }
        return iban.substring(0,4) + "****" + iban.substring(iban.length() - 4);
    }

    // Build an error callback response
    private CallbackResponse buildErrorResponse(String errorMessage) {
        return CallbackResponse.builder()
                .status("FAILED")
                .message("Bank linking failed")
                .error(errorMessage)
                .transactionsSynced(0)
                .build();
    }
}
