package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    Optional<BankAccount> findByPlaidAccountId(String plaidAccountId);

    List<BankAccount> findByUserAndStatus(User user, String status);

    List<BankAccount> findByStatus(String status);

    List<BankAccount> findByUser(User user);

    Optional<BankAccount> findByIdAndUser(Long id, User user);

    List<BankAccount> findByPlaidItem_Id(Long plaidItemId);

    List<BankAccount> findByPlaidItem_IdAndStatus(Long plaidItemId, String status);
}
