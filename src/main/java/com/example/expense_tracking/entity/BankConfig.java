package com.example.expense_tracking.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "account_number"})
})
@Data
public class BankConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "bank_name",  nullable = false)
    private String bankName;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "casso_api_key", nullable = false)
    private String cassoApiKey;

    @Column(name = "secure_token")
    private String secureToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
