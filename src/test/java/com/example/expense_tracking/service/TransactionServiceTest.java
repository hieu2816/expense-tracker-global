package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.DashBoardResponse;
import com.example.expense_tracking.dto.TransactionRequest;
import com.example.expense_tracking.dto.TransactionResponse;
import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ForbiddenException;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.BankAccountRepository;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Category testCategory;
    private Transaction testTransaction;
    private BankAccount testBankAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@example.com").build();
        testCategory = Category.builder().id(1L).name("Food").type(TransactionType.OUT).user(testUser).build();
        testBankAccount = BankAccount.builder().id(1L).name("My Bank").user(testUser).build();
        testTransaction = Transaction.builder()
                .id(1L)
                .amount(new BigDecimal("100.00"))
                .type(TransactionType.OUT)
                .category(testCategory)
                .user(testUser)
                .transactionDate(LocalDateTime.now())
                .build();
    }

    @Test
    void createTransaction_Success_NewCategory() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setType(TransactionType.IN);
        request.setCategory("Salary");

        when(categoryRepository.findByNameAndUser("Salary", testUser)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(
                Category.builder().id(2L).name("Salary").type(TransactionType.IN).user(testUser).build()
        );
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.createTransaction(request, testUser);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.getAmount());
        assertEquals("Salary", response.getCategory().getName());
        verify(categoryRepository).save(any(Category.class));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_ForbiddenBank() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setType(TransactionType.IN);
        request.setCategory("Salary");
        request.setBankAccountId(2L);

        User otherUser = User.builder().id(99L).build();
        BankAccount otherBank = BankAccount.builder().id(2L).user(otherUser).build();

        when(categoryRepository.findByNameAndUser(anyString(), any())).thenReturn(Optional.of(testCategory));
        when(bankAccountRepository.findById(2L)).thenReturn(Optional.of(otherBank));

        assertThrows(ForbiddenException.class, () -> transactionService.createTransaction(request, testUser));
    }

    @Test
    void deleteTransaction_Success() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        transactionService.deleteTransaction(1L, testUser);
        verify(transactionRepository).delete(testTransaction);
    }

    @Test
    void getDashBoardStats_Success() {
        when(transactionRepository.calculateTotal(testUser, TransactionType.IN)).thenReturn(new BigDecimal("1000.00"));
        when(transactionRepository.calculateTotal(testUser, TransactionType.OUT)).thenReturn(new BigDecimal("400.00"));

        DashBoardResponse stats = transactionService.getDashBoardStats(testUser);

        assertNotNull(stats);
        assertEquals(new BigDecimal("1000.00"), stats.getTotalIncome());
        assertEquals(new BigDecimal("400.00"), stats.getTotalExpense());
        assertEquals(new BigDecimal("600.00"), stats.getBalance());
    }

    @Test
    void updateTransaction_Success() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("150.00"));
        request.setType(TransactionType.OUT);
        request.setCategory("Food");
        request.setBankAccountId(0L); // switch to CASH

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(categoryRepository.findByNameAndUser("Food", testUser)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.updateTransaction(1L, request, testUser);

        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), response.getAmount());
        verify(transactionRepository).save(any(Transaction.class));
    }
}