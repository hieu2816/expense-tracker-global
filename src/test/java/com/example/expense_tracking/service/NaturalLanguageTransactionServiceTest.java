package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.NaturalLanguageConfirmRequest;
import com.example.expense_tracking.dto.NaturalLanguageDraftResponse;
import com.example.expense_tracking.dto.NaturalLanguageParseRequest;
import com.example.expense_tracking.dto.TransactionRequest;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.TransactionSource;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NaturalLanguageTransactionServiceTest {
    @Mock
    private CategoryRuleService categoryRuleService;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private NaturalLanguageTransactionService naturalLanguageService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
    }

    @Test
    void parseEnglishExpenseWithCategoryRule() {
        NaturalLanguageParseRequest request = new NaturalLanguageParseRequest();
        request.setText("grab 12.50 yesterday");
        Category transport = Category.builder().name("Transport").type(TransactionType.OUT).user(user).build();
        when(categoryRuleService.findMatchingCategory(anyString(), eq(TransactionType.OUT), eq(user)))
                .thenReturn(Optional.of(transport));

        NaturalLanguageDraftResponse draft = naturalLanguageService.parse(request, user);

        assertEquals(new BigDecimal("12.50"), draft.getAmount());
        assertEquals(TransactionType.OUT, draft.getType());
        assertEquals("Transport", draft.getCategory());
        assertEquals("grab", draft.getDescription());
        assertTrue(draft.getWarnings().isEmpty());
        assertTrue(draft.getConfidence().compareTo(new BigDecimal("0.90")) >= 0);
    }

    @Test
    void parseEnglishIncomeInfersCategory() {
        NaturalLanguageParseRequest request = new NaturalLanguageParseRequest();
        request.setText("salary 2.5k today");
        when(categoryRuleService.findMatchingCategory(anyString(), eq(TransactionType.IN), eq(user)))
                .thenReturn(Optional.empty());

        NaturalLanguageDraftResponse draft = naturalLanguageService.parse(request, user);

        assertEquals(new BigDecimal("2500.0"), draft.getAmount());
        assertEquals(TransactionType.IN, draft.getType());
        assertEquals("Income", draft.getCategory());
    }

    @Test
    void confirmCreatesNaturalLanguageTransactionWithSourceReference() {
        TransactionRequest transaction = new TransactionRequest();
        transaction.setAmount(new BigDecimal("12.00"));
        transaction.setType(TransactionType.OUT);
        transaction.setCategory("Food");
        transaction.setTransactionDate(LocalDateTime.of(2026, 6, 3, 0, 0));

        NaturalLanguageConfirmRequest request = new NaturalLanguageConfirmRequest();
        request.setOriginalText("lunch 12 today");
        request.setConfidence(new BigDecimal("0.80"));
        request.setTransaction(transaction);

        naturalLanguageService.confirm(request, user);

        ArgumentCaptor<String> sourceReferenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionService).createTransactionFromSource(eq(transaction), eq(user),
                eq(TransactionSource.NATURAL_LANGUAGE_EN), sourceReferenceCaptor.capture(),
                isNull(), eq("lunch 12 today"), eq(new BigDecimal("0.80")));
        assertTrue(sourceReferenceCaptor.getValue().startsWith("NATURAL_LANGUAGE_EN_"));
    }
}
