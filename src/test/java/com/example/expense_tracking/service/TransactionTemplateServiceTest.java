package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.TransactionRequest;
import com.example.expense_tracking.dto.TransactionTemplateRequest;
import com.example.expense_tracking.dto.TransactionTemplateResponse;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.TransactionSource;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.entity.TransactionTemplate;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.TransactionTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionTemplateServiceTest {
    @Mock
    private TransactionTemplateRepository templateRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionTemplateService templateService;

    private User user;
    private Category food;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        food = Category.builder().id(2L).name("Food").type(TransactionType.OUT).user(user).build();
    }

    @Test
    void createTemplateUsesExistingCategoryAndDefaultsCurrency() {
        TransactionTemplateRequest request = new TransactionTemplateRequest();
        request.setName("Lunch");
        request.setDescription("daily lunch");
        request.setAmount(new BigDecimal("12.00"));
        request.setType(TransactionType.OUT);
        request.setCategory("Food");
        request.setCurrency(null);

        when(categoryRepository.findByNameAndUser("Food", user)).thenReturn(Optional.of(food));
        when(templateRepository.save(any(TransactionTemplate.class))).thenAnswer(invocation -> {
            TransactionTemplate template = invocation.getArgument(0);
            template.setId(9L);
            return template;
        });

        TransactionTemplateResponse response = templateService.createTemplate(request, user);

        assertEquals("Lunch", response.getName());
        assertEquals("GBP", response.getCurrency());
        assertEquals("Food", response.getCategory().getName());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createTransactionFromTemplateUsesQuickTemplateSource() {
        TransactionTemplate template = TransactionTemplate.builder()
                .id(9L)
                .name("Lunch")
                .description("daily lunch")
                .amount(new BigDecimal("12.00"))
                .type(TransactionType.OUT)
                .category(food)
                .user(user)
                .currency("GBP")
                .active(true)
                .build();
        when(templateRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(template));

        templateService.createTransactionFromTemplate(9L, user);

        ArgumentCaptor<TransactionRequest> requestCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        ArgumentCaptor<String> sourceReferenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionService).createTransactionFromSource(requestCaptor.capture(), eq(user),
                eq(TransactionSource.QUICK_TEMPLATE), sourceReferenceCaptor.capture(),
                isNull(), isNull(), isNull());
        assertEquals("Food", requestCaptor.getValue().getCategory());
        assertEquals(new BigDecimal("12.00"), requestCaptor.getValue().getAmount());
        assertTrue(sourceReferenceCaptor.getValue().startsWith("TEMPLATE_9_"));
    }

    @Test
    void updateTemplateChangesFieldsAndCreatesCategory() {
        TransactionTemplate template = TransactionTemplate.builder()
                .id(9L)
                .name("Old")
                .amount(new BigDecimal("5.00"))
                .type(TransactionType.OUT)
                .category(food)
                .user(user)
                .currency("GBP")
                .active(true)
                .build();
        Category transport = Category.builder().id(3L).name("Transport").type(TransactionType.OUT).user(user).build();
        TransactionTemplateRequest request = new TransactionTemplateRequest();
        request.setName("Commute");
        request.setDescription("daily commute");
        request.setAmount(new BigDecimal("6.50"));
        request.setType(TransactionType.OUT);
        request.setCategory("Transport");
        request.setCurrency("USD");
        request.setActive(false);

        when(templateRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(template));
        when(categoryRepository.findByNameAndUser("Transport", user)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(transport);
        when(templateRepository.save(any(TransactionTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionTemplateResponse response = templateService.updateTemplate(9L, request, user);

        assertEquals("Commute", response.getName());
        assertEquals("Transport", response.getCategory().getName());
        assertEquals("USD", response.getCurrency());
        assertFalse(response.getActive());
    }

    @Test
    void deleteTemplateDeletesOwnedTemplate() {
        TransactionTemplate template = TransactionTemplate.builder().id(9L).user(user).category(food).build();
        when(templateRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(template));

        templateService.deleteTemplate(9L, user);

        verify(templateRepository).delete(template);
    }
}
