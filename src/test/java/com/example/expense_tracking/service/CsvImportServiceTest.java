package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.CsvColumnMapping;
import com.example.expense_tracking.dto.CsvImportPreviewResponse;
import com.example.expense_tracking.dto.CsvImportRequest;
import com.example.expense_tracking.dto.CsvImportResultResponse;
import com.example.expense_tracking.dto.TransactionRequest;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.ImportBatch;
import com.example.expense_tracking.entity.TransactionSource;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.ImportBatchRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private CategoryRuleService categoryRuleService;

    @InjectMocks
    private CsvImportService csvImportService;

    private User user;
    private CsvImportRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        request = new CsvImportRequest();
        request.setFileName("statement.csv");
        request.setContent("""
                date,description,amount,type,category
                2026-06-01,lunch,-12.50,,Food
                2026-06-01,lunch,-12.50,,Food
                bad-date,,0,,
                """);
        CsvColumnMapping mapping = new CsvColumnMapping();
        mapping.setDate("date");
        mapping.setDescription("description");
        mapping.setAmount("amount");
        mapping.setType("type");
        mapping.setCategory("category");
        request.setMapping(mapping);
    }

    @Test
    void previewClassifiesValidDuplicateAndInvalidRows() throws Exception {
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString())).thenReturn(false);

        CsvImportPreviewResponse preview = csvImportService.preview(request, user);

        assertEquals(3, preview.getTotalRows());
        assertEquals(1, preview.getValidRows());
        assertEquals(1, preview.getDuplicateRows());
        assertEquals(1, preview.getInvalidRows());
        assertEquals(TransactionType.OUT, preview.getRows().getFirst().getType());
        assertEquals(new BigDecimal("12.50"), preview.getRows().getFirst().getAmount());
    }

    @Test
    void commitCreatesOnlyValidNonDuplicateTransactions() throws Exception {
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CsvImportResultResponse result = csvImportService.commit(request, user);

        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getImportedRows());
        assertEquals(1, result.getDuplicateRows());
        assertEquals(1, result.getInvalidRows());

        ArgumentCaptor<TransactionRequest> transactionCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createTransactionFromSource(transactionCaptor.capture(), eq(user),
                eq(TransactionSource.CSV_IMPORT), startsWith("CSV_"), any(), isNull(), isNull());
        assertEquals("Food", transactionCaptor.getValue().getCategory());
        assertEquals(TransactionType.OUT, transactionCaptor.getValue().getType());
    }

    @Test
    void getImportBatchMapsRepositoryResult() {
        ImportBatch batch = ImportBatch.builder()
                .fileName("statement.csv")
                .source(TransactionSource.CSV_IMPORT)
                .totalRows(2)
                .importedRows(1)
                .duplicateRows(1)
                .invalidRows(0)
                .build();
        when(importBatchRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.of(batch));

        assertTrue(csvImportService.getImportBatch(java.util.UUID.randomUUID(), user).isPresent());
    }

    @Test
    void previewUsesCategoryRulesWhenCategoryColumnIsMissing() throws Exception {
        CsvImportRequest ruleRequest = new CsvImportRequest();
        ruleRequest.setFileName("statement.csv");
        ruleRequest.setContent("""
                date,description,amount
                01/06/2026,uber ride,-15.00
                """);
        CsvColumnMapping mapping = new CsvColumnMapping();
        mapping.setDate("date");
        mapping.setDescription("description");
        mapping.setAmount("amount");
        ruleRequest.setMapping(mapping);
        Category transport = Category.builder().name("Transport").type(TransactionType.OUT).user(user).build();
        when(categoryRuleService.findMatchingCategory("uber ride", TransactionType.OUT, user))
                .thenReturn(Optional.of(transport));
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString())).thenReturn(false);

        CsvImportPreviewResponse preview = csvImportService.preview(ruleRequest, user);

        assertEquals("Transport", preview.getRows().getFirst().getCategory());
        assertEquals(TransactionType.OUT, preview.getRows().getFirst().getType());
    }

    @Test
    void previewMarksDatabaseDuplicateAndParsesExplicitType() throws Exception {
        CsvImportRequest duplicateRequest = new CsvImportRequest();
        duplicateRequest.setFileName("statement.csv");
        duplicateRequest.setContent("""
                date,description,amount,type
                2026-06-01,salary,2500,CREDIT
                """);
        CsvColumnMapping mapping = new CsvColumnMapping();
        mapping.setDate("date");
        mapping.setDescription("description");
        mapping.setAmount("amount");
        mapping.setType("type");
        duplicateRequest.setMapping(mapping);
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString())).thenReturn(true);
        when(categoryRuleService.findMatchingCategory("salary", TransactionType.IN, user)).thenReturn(Optional.empty());

        CsvImportPreviewResponse preview = csvImportService.preview(duplicateRequest, user);

        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getDuplicateRows());
        assertEquals(TransactionType.IN, preview.getRows().getFirst().getType());
        assertEquals("Imported", preview.getRows().getFirst().getCategory());
    }

    @Test
    void getImportHistoryMapsBatches() {
        ImportBatch batch = ImportBatch.builder()
                .fileName("statement.csv")
                .source(TransactionSource.CSV_IMPORT)
                .totalRows(2)
                .importedRows(2)
                .duplicateRows(0)
                .invalidRows(0)
                .build();
        when(importBatchRepository.findTop20ByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(batch));

        assertEquals(1, csvImportService.getImportHistory(user).size());
    }
}
