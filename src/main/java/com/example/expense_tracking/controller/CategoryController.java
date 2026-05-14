package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.CategoryDTO;
import com.example.expense_tracking.dto.CategoryRequest;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {
    private final CategoryService categoryService;

    // List all categories for the current user.
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getUserCategories(
            @AuthenticationPrincipal User user) {
        log.debug("Fetching categories for user {}", user.getEmail());
        List<CategoryDTO> categories = categoryService.getUserCategories(user);
        return ResponseEntity.ok(categories);
    }

    // Create a new category.
    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CategoryRequest request) {
        System.out.println("Test DevOps Baseline");
        log.info("Creating category '{}' for user {}", request.getName(), user.getEmail());
        CategoryDTO category = categoryService.createCategory(user, request);
        return ResponseEntity.ok(category);
    }

    // Update a category.
    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        log.info("Updating category {} for user {}", id, user.getEmail());
        CategoryDTO category = categoryService.updateCategory(user, id, request);
        return ResponseEntity.ok(category);
    }

    // Delete a category.
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        log.info("Deleting category {} for user {}", id, user.getEmail());
        categoryService.deleteCategory(user, id);
        return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
    }
}
