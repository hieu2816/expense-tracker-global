package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.AttachmentResponse;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionAttachment;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.TransactionAttachmentRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    private final TransactionRepository transactionRepository;
    private final TransactionAttachmentRepository attachmentRepository;

    @Value("${app.receipt-upload-dir:uploads/receipts}")
    private String uploadDir;

    @Transactional
    public AttachmentResponse upload(Long transactionId, MultipartFile file, User user) throws IOException {
        Transaction transaction = transactionRepository.findByIdAndUser(transactionId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        Path userDir = Paths.get(uploadDir, user.getId().toString()).toAbsolutePath().normalize();
        Files.createDirectories(userDir);
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt";
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = userDir.resolve(UUID.randomUUID() + "_" + safeName).normalize();
        file.transferTo(target);

        TransactionAttachment attachment = attachmentRepository.save(TransactionAttachment.builder()
                .transaction(transaction)
                .user(user)
                .fileName(originalName)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .storagePath(target.toString())
                .build());
        return mapToResponse(attachment);
    }

    public List<AttachmentResponse> list(Long transactionId, User user) {
        Transaction transaction = transactionRepository.findByIdAndUser(transactionId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return attachmentRepository.findByTransactionAndUserOrderByCreatedAtDesc(transaction, user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AttachmentDownload getDownload(Long id, User user) {
        TransactionAttachment attachment = attachmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        Resource resource = new FileSystemResource(attachment.getStoragePath());
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Attachment file not found");
        }
        return new AttachmentDownload(attachment.getFileName(), attachment.getContentType(), resource);
    }

    @Transactional
    public void delete(Long id, User user) throws IOException {
        TransactionAttachment attachment = attachmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        Files.deleteIfExists(Path.of(attachment.getStoragePath()));
        attachmentRepository.delete(attachment);
    }

    private AttachmentResponse mapToResponse(TransactionAttachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .transactionId(attachment.getTransaction().getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    public record AttachmentDownload(String fileName, String contentType, Resource resource) {
    }
}
