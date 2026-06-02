package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.CategoryRuleRequest;
import com.example.expense_tracking.dto.CategoryRuleResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.CategoryRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/category-rules")
@RequiredArgsConstructor
public class CategoryRuleController {
    private final CategoryRuleService categoryRuleService;

    @GetMapping
    public ResponseEntity<List<CategoryRuleResponse>> getRules(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(categoryRuleService.getRules(user));
    }

    @PostMapping
    public ResponseEntity<CategoryRuleResponse> createRule(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CategoryRuleRequest request) {
        return ResponseEntity.ok(categoryRuleService.createRule(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryRuleResponse> updateRule(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRuleRequest request) {
        return ResponseEntity.ok(categoryRuleService.updateRule(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@AuthenticationPrincipal User user, @PathVariable Long id) {
        categoryRuleService.deleteRule(id, user);
        return ResponseEntity.noContent().build();
    }
}
