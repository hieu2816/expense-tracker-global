package com.example.expense_tracking.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_logs")
@Data
@NoArgsConstructor
public class WebhookLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at", updatable = false)
    private LocalDateTime receivedAt;

    // Look for any @PrePersist before Hibernate sends the SQL to db
    @PrePersist
    protected void onCreate() {
        // This will take the time at the moment and pass to receivedAt above
        this.receivedAt = LocalDateTime.now();
    }
}
