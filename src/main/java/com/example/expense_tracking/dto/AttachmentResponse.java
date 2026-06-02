package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AttachmentResponse {
    private Long id;
    private Long transactionId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
