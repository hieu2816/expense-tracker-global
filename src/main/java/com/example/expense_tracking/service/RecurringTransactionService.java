package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.*;
import com.example.expense_tracking.entity.*;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.RecurringTransactionRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import com.example.expense_tracking.repository.TransactionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionService {
    private final RecurringTransactionRepository recurringRepository;
    private final TransactionTemplateRepository templateRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final TransactionTemplateService templateService;

    public List<RecurringTransactionResponse> getRecurring(User user) {
        return recurringRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public RecurringTransactionResponse createRecurring(RecurringTransactionRequest request, User user) {
        TransactionTemplate template = templateRepository.findByIdAndUser(request.getTemplateId(), user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction template not found"));
        RecurringTransaction recurring = RecurringTransaction.builder()
                .user(user)
                .template(template)
                .frequency(request.getFrequency())
                .nextRunDate(request.getNextRunDate())
                .active(request.getActive() == null || request.getActive())
                .build();
        return mapToResponse(recurringRepository.save(recurring));
    }

    @Transactional
    public RecurringTransactionResponse updateRecurring(Long id, RecurringTransactionRequest request, User user) {
        RecurringTransaction recurring = recurringRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));
        TransactionTemplate template = templateRepository.findByIdAndUser(request.getTemplateId(), user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction template not found"));
        recurring.setTemplate(template);
        recurring.setFrequency(request.getFrequency());
        recurring.setNextRunDate(request.getNextRunDate());
        recurring.setActive(request.getActive() == null || request.getActive());
        return mapToResponse(recurringRepository.save(recurring));
    }

    @Transactional
    public void deleteRecurring(Long id, User user) {
        RecurringTransaction recurring = recurringRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));
        recurringRepository.delete(recurring);
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void runDueRecurringTransactions() {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> due = recurringRepository.findByActiveTrueAndNextRunDateLessThanEqual(today);
        for (RecurringTransaction recurring : due) {
            try {
                createDueTransaction(recurring, today);
            } catch (RuntimeException ex) {
                log.error("Failed to create recurring transaction {}", recurring.getId(), ex);
            }
        }
    }

    private void createDueTransaction(RecurringTransaction recurring, LocalDate runDate) {
        String sourceReference = "RECURRING_" + recurring.getId() + "_" + recurring.getNextRunDate();
        if (!transactionRepository.existsByUserAndSourceReference(recurring.getUser(), sourceReference)) {
            TransactionTemplate template = recurring.getTemplate();
            TransactionRequest request = new TransactionRequest();
            request.setCategory(template.getCategory().getName());
            request.setAmount(template.getAmount());
            request.setType(template.getType());
            request.setDescription(template.getDescription());
            request.setTransactionDate(recurring.getNextRunDate().atStartOfDay());
            transactionService.createTransactionFromSource(request, recurring.getUser(), TransactionSource.RECURRING,
                    sourceReference, null, null, null);
        }
        recurring.setLastRunDate(runDate);
        recurring.setNextRunDate(nextDate(recurring.getNextRunDate(), recurring.getFrequency()));
        recurringRepository.save(recurring);
    }

    private LocalDate nextDate(LocalDate date, RecurringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case YEARLY -> date.plusYears(1);
        };
    }

    private RecurringTransactionResponse mapToResponse(RecurringTransaction recurring) {
        return RecurringTransactionResponse.builder()
                .id(recurring.getId())
                .template(templateService.mapToResponse(recurring.getTemplate()))
                .frequency(recurring.getFrequency())
                .nextRunDate(recurring.getNextRunDate())
                .lastRunDate(recurring.getLastRunDate())
                .active(recurring.getActive())
                .build();
    }
}
