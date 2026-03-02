package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.CategoryRequest;
import com.example.expense_tracking.entity.Category;
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

    // List all categories for the current user
    // GET /api/categories
    @GetMapping
    public ResponseEntity<List<Category>> getUserCategories(
            @AuthenticationPrincipal User user) {
        log.debug("Fetching categories for user {}", user.getEmail());
        List<Category> categories = categoryService.getUserCategories(user);
        return ResponseEntity.ok(categories);
    }

    // Create a new category
    // POST /api/categories
    @PostMapping
    public ResponseEntity<Category> createCategory(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CategoryRequest request) {
        log.info("Creating category '{}' for user {}", request.getName(), user.getEmail());
        Category category = categoryService.createCategory(user, request);
        return ResponseEntity.ok(category);
    }

    // Update a category
    // PUT /api/categories/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        log.info("Updating category {} for user {}", id, user.getEmail());
        Category category = categoryService.updateCategory(user, id, request);
        return ResponseEntity.ok(category);
    }

    // Delete a category
    // DELETE /api/categories/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        log.info("Deleting category {} for user {}", id, user.getEmail());
        categoryService.deleteCategory(user, id);
        return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
    }
}
