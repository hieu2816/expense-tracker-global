package com.example.expense_tracking.entity;

import com.example.expense_tracking.utils.AccessTokenEncryptor;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plaid_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaidItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "item_id", nullable = false, unique = true)
    private String itemId;

    @Convert(converter = AccessTokenEncryptor.class)
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "sync_cursor", columnDefinition = "TEXT")
    private String syncCursor;

    @Column(name = "institution_id")
    private String institutionId;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "institution_logo")
    private String institutionLogo;

    @Column(name = "status")
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
