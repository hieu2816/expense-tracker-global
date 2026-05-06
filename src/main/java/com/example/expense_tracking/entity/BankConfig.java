package com.example.expense_tracking.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_configs")
@Data
public class BankConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "institution_id")
    private String institutionId; // e.g., "REVOLUT_REVOGB21"

    @Column(name = "institution_name")
    private String institutionName; // e.g., "Revolut"

    @Column(name = "institution_logo")
    private String institutionLogo; // URL to bank logo

    @Column(name = "requisition_id")
    private String requisitionId; // GoCardless session ID

    @Column(name = "link_reference", unique = true)
    private String linkReference; // UUID reference for callback identification

    @Column(name = "gocardless_account_id", unique = true)
    private String gocardlessAccountId; // Account ID for fetching transactions

    @Column(name = "iban")
    private String iban; // e.g., "GB29NWBK60161331926819"

    @Column(name = "account_name")
    private String accountName; // Account holder name

    @Column(name = "status")
    private String status = "PENDING"; // PENDING, LINKED, EXPIRED, ERROR

    @Column(name = "access_expires_at")
    private LocalDateTime accessExpiresAt; // When consent expires

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt; // Last successful sync
}
