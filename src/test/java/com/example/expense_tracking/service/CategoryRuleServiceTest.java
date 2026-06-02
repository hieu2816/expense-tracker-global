package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.CategoryRuleRequest;
import com.example.expense_tracking.dto.CategoryRuleResponse;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.CategoryRule;
import com.example.expense_tracking.entity.CategoryRuleMatchType;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.CategoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryRuleServiceTest {
    @Mock
    private CategoryRuleRepository categoryRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryRuleService categoryRuleService;

    private User user;
    private Category transport;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        transport = Category.builder().id(10L).name("Transport").type(TransactionType.OUT).user(user).build();
    }

    @Test
    void createRuleCreatesCategoryWhenMissing() {
        CategoryRuleRequest request = new CategoryRuleRequest();
        request.setKeyword(" grab ");
        request.setMatchType(CategoryRuleMatchType.CONTAINS);
        request.setCategory("Transport");
        request.setTransactionType(TransactionType.OUT);

        when(categoryRepository.findByNameAndUser("Transport", user)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(transport);
        when(categoryRuleRepository.save(any(CategoryRule.class))).thenAnswer(invocation -> {
            CategoryRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        CategoryRuleResponse response = categoryRuleService.createRule(request, user);

        assertEquals("grab", response.getKeyword());
        assertEquals("Transport", response.getCategory().getName());
        assertEquals(100, response.getPriority());
        assertTrue(response.getActive());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void findMatchingCategorySupportsAllMatchTypesAndPriority() {
        Category exactIncome = Category.builder().id(11L).name("Income").type(TransactionType.IN).user(user).build();
        List<CategoryRule> rules = List.of(
                rule("taxi", CategoryRuleMatchType.CONTAINS, transport, TransactionType.OUT, 10),
                rule("grab", CategoryRuleMatchType.STARTS_WITH, transport, TransactionType.OUT, 20),
                rule("salary", CategoryRuleMatchType.EXACT, exactIncome, TransactionType.IN, 30),
                rule("uber|bolt", CategoryRuleMatchType.REGEX, transport, TransactionType.OUT, 40)
        );
        when(categoryRuleRepository.findByUserAndActiveTrueOrderByPriorityAscCreatedAtAsc(user)).thenReturn(rules);

        assertEquals("Transport", categoryRuleService.findMatchingCategory("airport taxi", TransactionType.OUT, user).orElseThrow().getName());
        assertEquals("Transport", categoryRuleService.findMatchingCategory("grab ride", TransactionType.OUT, user).orElseThrow().getName());
        assertEquals("Income", categoryRuleService.findMatchingCategory("salary", TransactionType.IN, user).orElseThrow().getName());
        assertEquals("Transport", categoryRuleService.findMatchingCategory("bolt ride", TransactionType.OUT, user).orElseThrow().getName());
        assertTrue(categoryRuleService.findMatchingCategory("salary bonus", TransactionType.IN, user).isEmpty());
    }

    @Test
    void invalidRegexDoesNotMatch() {
        when(categoryRuleRepository.findByUserAndActiveTrueOrderByPriorityAscCreatedAtAsc(user))
                .thenReturn(List.of(rule("[", CategoryRuleMatchType.REGEX, transport, TransactionType.OUT, 1)));

        assertTrue(categoryRuleService.findMatchingCategory("anything", TransactionType.OUT, user).isEmpty());
    }

    private CategoryRule rule(String keyword, CategoryRuleMatchType matchType, Category category, TransactionType type, int priority) {
        return CategoryRule.builder()
                .keyword(keyword)
                .matchType(matchType)
                .category(category)
                .transactionType(type)
                .priority(priority)
                .active(true)
                .user(user)
                .build();
    }
}
