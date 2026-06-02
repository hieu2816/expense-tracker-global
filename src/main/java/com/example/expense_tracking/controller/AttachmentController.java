package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.AttachmentResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AttachmentController {
    private final AttachmentService attachmentService;

    @PostMapping(value = "/api/transactions/{transactionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @AuthenticationPrincipal User user,
            @PathVariable Long transactionId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(attachmentService.upload(transactionId, file, user));
    }

    @GetMapping("/api/transactions/{transactionId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> getAttachments(
            @AuthenticationPrincipal User user,
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(attachmentService.list(transactionId, user));
    }

    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        AttachmentService.AttachmentDownload download = attachmentService.getDownload(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .body(download.resource());
    }

    @DeleteMapping("/api/attachments/{id}")
    public ResponseEntity<Void> deleteAttachment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) throws IOException {
        attachmentService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
