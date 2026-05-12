package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaidItemRepository extends JpaRepository<PlaidItem, Long> {
    Optional<PlaidItem> findByItemId(String itemId);

    List<PlaidItem> findByUserAndStatus(User user, String status);

    List<PlaidItem> findByStatus(String status);
}
