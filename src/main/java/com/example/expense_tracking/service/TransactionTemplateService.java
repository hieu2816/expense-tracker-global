package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.*;
import com.example.expense_tracking.entity.*;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.TransactionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionTemplateService {
    private final TransactionTemplateRepository templateRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;

    public List<TransactionTemplateResponse> getTemplates(User user) {
        return templateRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public TransactionTemplateResponse createTemplate(TransactionTemplateRequest request, User user) {
        Category category = findOrCreateCategory(request.getCategory(), request.getType(), user);
        TransactionTemplate template = TransactionTemplate.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .amount(request.getAmount())
                .type(request.getType())
                .category(category)
                .currency(request.getCurrency() != null ? request.getCurrency() : "GBP")
                .active(request.getActive() == null || request.getActive())
                .build();
        return mapToResponse(templateRepository.save(template));
    }

    @Transactional
    public TransactionTemplateResponse updateTemplate(Long id, TransactionTemplateRequest request, User user) {
        TransactionTemplate template = templateRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction template not found"));
        Category category = findOrCreateCategory(request.getCategory(), request.getType(), user);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setAmount(request.getAmount());
        template.setType(request.getType());
        template.setCategory(category);
        template.setCurrency(request.getCurrency() != null ? request.getCurrency() : "GBP");
        template.setActive(request.getActive() == null || request.getActive());
        return mapToResponse(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long id, User user) {
        TransactionTemplate template = templateRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction template not found"));
        templateRepository.delete(template);
    }

    @Transactional
    public TransactionResponse createTransactionFromTemplate(Long id, User user) {
        TransactionTemplate template = templateRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction template not found"));
        TransactionRequest request = new TransactionRequest();
        request.setCategory(template.getCategory().getName());
        request.setAmount(template.getAmount());
        request.setType(template.getType());
        request.setDescription(template.getDescription());
        request.setTransactionDate(LocalDateTime.now());
        return transactionService.createTransactionFromSource(request, user, TransactionSource.QUICK_TEMPLATE,
                "TEMPLATE_" + template.getId() + "_" + System.currentTimeMillis(), null, null, null);
    }

    public TransactionTemplateResponse mapToResponse(TransactionTemplate template) {
        return TransactionTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .amount(template.getAmount())
                .type(template.getType())
                .category(CategoryDTO.builder()
                        .id(template.getCategory().getId())
                        .name(template.getCategory().getName())
                        .type(template.getCategory().getType())
                        .build())
                .currency(template.getCurrency())
                .active(template.getActive())
                .build();
    }

    private Category findOrCreateCategory(String categoryName, TransactionType type, User user) {
        return categoryRepository.findByNameAndUser(categoryName, user)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(categoryName)
                        .type(type)
                        .user(user)
                        .build()));
    }
}
