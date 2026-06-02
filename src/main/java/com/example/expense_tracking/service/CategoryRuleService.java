package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.CategoryDTO;
import com.example.expense_tracking.dto.CategoryRuleRequest;
import com.example.expense_tracking.dto.CategoryRuleResponse;
import com.example.expense_tracking.entity.*;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.CategoryRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
@RequiredArgsConstructor
public class CategoryRuleService {
    private final CategoryRuleRepository categoryRuleRepository;
    private final CategoryRepository categoryRepository;

    public List<CategoryRuleResponse> getRules(User user) {
        return categoryRuleRepository.findByUserOrderByPriorityAscCreatedAtAsc(user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public CategoryRuleResponse createRule(CategoryRuleRequest request, User user) {
        Category category = findOrCreateCategory(request.getCategory(), request.getTransactionType(), user);
        CategoryRule rule = CategoryRule.builder()
                .user(user)
                .keyword(request.getKeyword().trim())
                .matchType(request.getMatchType())
                .category(category)
                .transactionType(request.getTransactionType())
                .priority(request.getPriority() != null ? request.getPriority() : 100)
                .active(request.getActive() == null || request.getActive())
                .build();
        return mapToResponse(categoryRuleRepository.save(rule));
    }

    @Transactional
    public CategoryRuleResponse updateRule(Long id, CategoryRuleRequest request, User user) {
        CategoryRule rule = categoryRuleRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Category rule not found"));
        Category category = findOrCreateCategory(request.getCategory(), request.getTransactionType(), user);
        rule.setKeyword(request.getKeyword().trim());
        rule.setMatchType(request.getMatchType());
        rule.setCategory(category);
        rule.setTransactionType(request.getTransactionType());
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        rule.setActive(request.getActive() == null || request.getActive());
        return mapToResponse(categoryRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long id, User user) {
        CategoryRule rule = categoryRuleRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Category rule not found"));
        categoryRuleRepository.delete(rule);
    }

    public Optional<Category> findMatchingCategory(String description, TransactionType type, User user) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String normalized = description.trim().toLowerCase();
        return categoryRuleRepository.findByUserAndActiveTrueOrderByPriorityAscCreatedAtAsc(user)
                .stream()
                .filter(rule -> rule.getTransactionType() == null || rule.getTransactionType() == type)
                .filter(rule -> matches(rule, normalized))
                .map(CategoryRule::getCategory)
                .findFirst();
    }

    private boolean matches(CategoryRule rule, String normalizedDescription) {
        String keyword = rule.getKeyword() == null ? "" : rule.getKeyword().trim().toLowerCase();
        return switch (rule.getMatchType()) {
            case CONTAINS -> normalizedDescription.contains(keyword);
            case STARTS_WITH -> normalizedDescription.startsWith(keyword);
            case EXACT -> normalizedDescription.equals(keyword);
            case REGEX -> matchesRegex(rule.getKeyword(), normalizedDescription);
        };
    }

    private boolean matchesRegex(String pattern, String value) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private Category findOrCreateCategory(String categoryName, TransactionType type, User user) {
        TransactionType categoryType = type != null ? type : TransactionType.OUT;
        return categoryRepository.findByNameAndUser(categoryName, user)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(categoryName)
                        .type(categoryType)
                        .user(user)
                        .build()));
    }

    private CategoryRuleResponse mapToResponse(CategoryRule rule) {
        return CategoryRuleResponse.builder()
                .id(rule.getId())
                .keyword(rule.getKeyword())
                .matchType(rule.getMatchType())
                .category(CategoryDTO.builder()
                        .id(rule.getCategory().getId())
                        .name(rule.getCategory().getName())
                        .type(rule.getCategory().getType())
                        .build())
                .transactionType(rule.getTransactionType())
                .priority(rule.getPriority())
                .active(rule.getActive())
                .build();
    }
}
