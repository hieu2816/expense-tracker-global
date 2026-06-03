package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.CategoryDTO;
import com.example.expense_tracking.dto.RecurringTransactionRequest;
import com.example.expense_tracking.dto.TransactionRequest;
import com.example.expense_tracking.dto.TransactionTemplateResponse;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.RecurringFrequency;
import com.example.expense_tracking.entity.RecurringTransaction;
import com.example.expense_tracking.entity.TransactionSource;
import com.example.expense_tracking.entity.TransactionTemplate;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.RecurringTransactionRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import com.example.expense_tracking.repository.TransactionTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {
    @Mock
    private RecurringTransactionRepository recurringRepository;

    @Mock
    private TransactionTemplateRepository templateRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private TransactionTemplateService templateService;

    @InjectMocks
    private RecurringTransactionService recurringService;

    private User user;
    private Category rent;
    private TransactionTemplate template;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        rent = Category.builder().id(2L).name("Rent").type(TransactionType.OUT).user(user).build();
        template = TransactionTemplate.builder()
                .id(10L)
                .name("Rent")
                .description("monthly rent")
                .amount(new BigDecimal("900.00"))
                .type(TransactionType.OUT)
                .category(rent)
                .user(user)
                .currency("GBP")
                .active(true)
                .build();
    }

    @Test
    void createRecurringBuildsRuleFromTemplate() {
        RecurringTransactionRequest request = new RecurringTransactionRequest();
        request.setTemplateId(10L);
        request.setFrequency(RecurringFrequency.MONTHLY);
        request.setNextRunDate(LocalDate.of(2026, 6, 1));

        when(templateRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(template));
        when(recurringRepository.save(any(RecurringTransaction.class))).thenAnswer(invocation -> {
            RecurringTransaction recurring = invocation.getArgument(0);
            recurring.setId(5L);
            return recurring;
        });
        when(templateService.mapToResponse(template)).thenReturn(templateResponse());

        var response = recurringService.createRecurring(request, user);

        assertEquals(5L, response.getId());
        assertEquals(RecurringFrequency.MONTHLY, response.getFrequency());
        assertEquals(LocalDate.of(2026, 6, 1), response.getNextRunDate());
        assertTrue(response.getActive());
    }

    @Test
    void runDueRecurringTransactionsCreatesTransactionAndAdvancesNextDate() {
        RecurringTransaction recurring = RecurringTransaction.builder()
                .id(5L)
                .user(user)
                .template(template)
                .frequency(RecurringFrequency.MONTHLY)
                .nextRunDate(LocalDate.now().minusDays(1))
                .active(true)
                .build();
        when(recurringRepository.findByActiveTrueAndNextRunDateLessThanEqual(any(LocalDate.class)))
                .thenReturn(List.of(recurring));
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString())).thenReturn(false);

        recurringService.runDueRecurringTransactions();

        ArgumentCaptor<TransactionRequest> transactionCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createTransactionFromSource(transactionCaptor.capture(), eq(user),
                eq(TransactionSource.RECURRING), startsWith("RECURRING_5_"),
                isNull(), isNull(), isNull());
        assertEquals("Rent", transactionCaptor.getValue().getCategory());
        assertEquals(new BigDecimal("900.00"), transactionCaptor.getValue().getAmount());
        assertEquals(LocalDate.now(), recurring.getLastRunDate());
        assertEquals(LocalDate.now().minusDays(1).plusMonths(1), recurring.getNextRunDate());
        verify(recurringRepository).save(recurring);
    }

    @Test
    void runDueRecurringTransactionsDoesNotDuplicateExistingSourceReference() {
        RecurringTransaction recurring = RecurringTransaction.builder()
                .id(5L)
                .user(user)
                .template(template)
                .frequency(RecurringFrequency.WEEKLY)
                .nextRunDate(LocalDate.now())
                .active(true)
                .build();
        when(recurringRepository.findByActiveTrueAndNextRunDateLessThanEqual(any(LocalDate.class)))
                .thenReturn(List.of(recurring));
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString())).thenReturn(true);

        recurringService.runDueRecurringTransactions();

        verify(transactionService, never()).createTransactionFromSource(any(), any(), any(), any(), any(), any(), any());
        assertEquals(LocalDate.now().plusWeeks(1), recurring.getNextRunDate());
        verify(recurringRepository).save(recurring);
    }

    @Test
    void updateRecurringChangesTemplateFrequencyDateAndActiveFlag() {
        RecurringTransaction recurring = RecurringTransaction.builder()
                .id(5L)
                .user(user)
                .template(template)
                .frequency(RecurringFrequency.MONTHLY)
                .nextRunDate(LocalDate.of(2026, 6, 1))
                .active(true)
                .build();
        RecurringTransactionRequest request = new RecurringTransactionRequest();
        request.setTemplateId(10L);
        request.setFrequency(RecurringFrequency.YEARLY);
        request.setNextRunDate(LocalDate.of(2027, 1, 1));
        request.setActive(false);
        when(recurringRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(recurring));
        when(templateRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(template));
        when(recurringRepository.save(recurring)).thenReturn(recurring);
        when(templateService.mapToResponse(template)).thenReturn(templateResponse());

        var response = recurringService.updateRecurring(5L, request, user);

        assertEquals(RecurringFrequency.YEARLY, response.getFrequency());
        assertEquals(LocalDate.of(2027, 1, 1), response.getNextRunDate());
        assertFalse(response.getActive());
    }

    @Test
    void deleteRecurringDeletesOwnedRule() {
        RecurringTransaction recurring = RecurringTransaction.builder().id(5L).user(user).template(template).build();
        when(recurringRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(recurring));

        recurringService.deleteRecurring(5L, user);

        verify(recurringRepository).delete(recurring);
    }

    @Test
    void runDueRecurringTransactionsContinuesWhenOneRuleFails() {
        RecurringTransaction broken = RecurringTransaction.builder()
                .id(5L)
                .user(user)
                .template(template)
                .frequency(RecurringFrequency.DAILY)
                .nextRunDate(LocalDate.now())
                .active(true)
                .build();
        RecurringTransaction yearly = RecurringTransaction.builder()
                .id(6L)
                .user(user)
                .template(template)
                .frequency(RecurringFrequency.YEARLY)
                .nextRunDate(LocalDate.now())
                .active(true)
                .build();
        when(recurringRepository.findByActiveTrueAndNextRunDateLessThanEqual(any(LocalDate.class)))
                .thenReturn(List.of(broken, yearly));
        when(transactionRepository.existsByUserAndSourceReference(eq(user), anyString()))
                .thenReturn(false);
        doThrow(new RuntimeException("boom")).doReturn(null)
                .when(transactionService)
                .createTransactionFromSource(any(), eq(user), eq(TransactionSource.RECURRING), anyString(),
                        isNull(), isNull(), isNull());

        recurringService.runDueRecurringTransactions();

        assertEquals(LocalDate.now().plusYears(1), yearly.getNextRunDate());
        verify(recurringRepository).save(yearly);
    }

    private TransactionTemplateResponse templateResponse() {
        return TransactionTemplateResponse.builder()
                .id(10L)
                .name("Rent")
                .description("monthly rent")
                .amount(new BigDecimal("900.00"))
                .type(TransactionType.OUT)
                .category(CategoryDTO.builder().id(2L).name("Rent").type(TransactionType.OUT).build())
                .currency("GBP")
                .active(true)
                .build();
    }
}
