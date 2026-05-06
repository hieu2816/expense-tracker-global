package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.*;
import com.example.expense_tracking.entity.*;
import com.example.expense_tracking.exception.ForbiddenException;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.BankConfigRepository;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BankConfigRepository bankConfigRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request, User user) {
        LocalDateTime actualDate = request.getTransactionDate();
        if (actualDate == null) {
            actualDate = LocalDateTime.now();
        }

        String categoryName = request.getCategory();

        Category category = categoryRepository.findByNameAndUser(categoryName, user)
                .orElseGet(() -> {
                    Category newCategory = Category.builder()
                            .name(categoryName)
                            .type(request.getType())
                            .user(user)
                            .build();
                    return categoryRepository.save(newCategory);
                });

        BankConfig bankConfig = null;
        // Check if there is Bank or Cash
        if (request.getBankConfigId() != null) {
            bankConfig = bankConfigRepository.findById(request.getBankConfigId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bank Account not found"));

            // If there is bank. Security Check: Does user own this bank account?
            if (!bankConfig.getUser().getId().equals(user.getId())) {
                throw new ForbiddenException("You do not own this bank account");
            }
        }

        // Auto generate Manual ID
        String manualId = "MANUAL_" + UUID.randomUUID();

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .type(request.getType())
                .amount(request.getAmount())
                .description(request.getDescription())
                .transactionDate(actualDate)
                .bankConfig(bankConfig)
                .bankTransactionId(manualId)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        return mapToResponse(saved);
    }

    public Page<TransactionResponse> getAllTransactions(User user, int page, int size, String category,
            LocalDateTime startDate, LocalDateTime endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        Page<Transaction> transactionPage = transactionRepository.findFilteredTransactions(user, category, startDate,
                endDate, pageable);

        return transactionPage.map(this::mapToResponse);
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, TransactionRequest request, User user) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        // Check if User own the transaction
        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not own this transaction");
        }

        // Update field
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        if (request.getTransactionDate() != null) {
            transaction.setTransactionDate(request.getTransactionDate());
        }

        // Save Category if user change Category
        String categoryName = request.getCategory();
        Category category = categoryRepository.findByNameAndUser(categoryName, user)
                .orElseGet(() -> {
                    Category newCategory = Category.builder()
                            .name(categoryName)
                            .type(request.getType())
                            .user(user)
                            .build();
                    return categoryRepository.save(newCategory);
                });
        transaction.setCategory(category);

        if (request.getBankConfigId() != null) {
            if (request.getBankConfigId() <= 0) {
                // Case A: User sent 0 or negative -> Explicitly switch to CASH
                transaction.setBankConfig(null);
            } else {
                // Case B: User sent a positive ID -> Switch to NEW BANK
                BankConfig bankConfig = bankConfigRepository.findById(request.getBankConfigId())
                        .orElseThrow(() -> new ResourceNotFoundException("Bank Account not found"));
                if (!bankConfig.getUser().getId().equals(user.getId())) {
                    throw new ForbiddenException("You do not own this bank account");
                }
                transaction.setBankConfig(bankConfig);
            }
        }
        // Case C: If request.getAccountNumber() is NULL -> Do NOTHING (Keep old
        // Bank/Cash)

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Convert to Response DTO
        return mapToResponse(savedTransaction);
    }

    @Transactional
    public void deleteTransaction(Long id, User user) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not own this transaction");
        }

        transactionRepository.delete(transaction);
    }

    public DashBoardResponse getDashBoardStats(User user) {
        BigDecimal totalIncome = transactionRepository.calculateTotal(user, TransactionType.IN);
        BigDecimal totalExpense = transactionRepository.calculateTotal(user, TransactionType.OUT);
        BigDecimal balance = totalIncome.subtract(totalExpense);

        return DashBoardResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(balance)
                .build();
    }

    public List<CategorySummaryResponse> getCategorySummary(User user) {
        return transactionRepository.getCategorySummary(user);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        CategoryDTO categoryDTO = null;
        if (transaction.getCategory() != null) {
            categoryDTO = CategoryDTO.builder()
                    .id(transaction.getCategory().getId())
                    .name(transaction.getCategory().getName())
                    .type(transaction.getCategory().getType())
                    .build();
        }

        return TransactionResponse.builder()
                .id(transaction.getId())
                .category(categoryDTO)
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }

    public void exportToCsv(
            User user,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Writer writer) throws IOException {
        Pageable pageable = PageRequest.of(0, 100000, Sort.by("transactionDate").descending());
        Page<Transaction> page = transactionRepository.findFilteredTransactions(user, category, startDate, endDate,
                pageable);
        List<Transaction> transactions = page.getContent();

        try (CSVPrinter csvPrinter = new CSVPrinter(writer,
                CSVFormat.DEFAULT.withHeader("ID", "Date", "Type", "Category", "Amount", "Description"))) {
            for (Transaction t : transactions) {
                csvPrinter.printRecord(
                        t.getId(),
                        t.getTransactionDate(),
                        t.getType(),
                        t.getCategory() != null ? sanitizeCsvValue(t.getCategory().getName()) : "Uncategorized",
                        t.getAmount(),
                        sanitizeCsvValue(t.getDescription()));
            }
        }
    }

    // Prevent CSV injection by prefixing dangerous characters with a single quote
    private String sanitizeCsvValue(String value) {
        if (value == null)
            return "";
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-")
                || value.startsWith("@") || value.startsWith("\t") || value.startsWith("\r")) {
            return "'" + value;
        }
        return value;
    }
}
