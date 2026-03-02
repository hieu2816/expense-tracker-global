package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.CategoryRequest;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    // Get all categories for a user
    public List<Category> getUserCategories(User user) {
        return categoryRepository.findByUser(user);
    }

    // Create a new category
    public Category createCategory(User user, CategoryRequest request) {
        // Check if category with same name already exists for this user
        categoryRepository.findByNameAndUser(request.getName(), user)
                .ifPresent(existing -> {
                    throw new RuntimeException("Category '" + request.getName() + "' already exists");
                });

        Category category = Category.builder()
                .user(user)
                .name(request.getName())
                .type(request.getType())
                .icon(request.getIcon())
                .build();

        return categoryRepository.save(category);
    }

    // Update a category
    public Category updateCategory(User user, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        category.setName(request.getName());
        category.setType(request.getType());
        category.setIcon(request.getIcon());

        return categoryRepository.save(category);
    }

    // Delete a category
    public void deleteCategory(User user, Long categoryId) {
        Category category = categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        categoryRepository.delete(category);
    }
}
