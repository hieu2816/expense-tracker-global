package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.AttachmentResponse;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionAttachment;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.TransactionAttachmentRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionAttachmentRepository attachmentRepository;

    @InjectMocks
    private AttachmentService attachmentService;

    @TempDir
    private Path uploadDir;

    private User user;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        transaction = Transaction.builder().id(10L).user(user).build();
        ReflectionTestUtils.setField(attachmentService, "uploadDir", uploadDir.toString());
    }

    @Test
    void uploadStoresFileAndCreatesAttachmentRecord() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt 1.pdf",
                "application/pdf", "content".getBytes());
        when(transactionRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(transaction));
        when(attachmentRepository.save(any(TransactionAttachment.class))).thenAnswer(invocation -> {
            TransactionAttachment attachment = invocation.getArgument(0);
            attachment.setId(20L);
            return attachment;
        });

        AttachmentResponse response = attachmentService.upload(10L, file, user);

        assertEquals(20L, response.getId());
        assertEquals("receipt 1.pdf", response.getFileName());
        assertEquals("application/pdf", response.getContentType());
        assertTrue(Files.list(uploadDir.resolve("1")).findAny().isPresent());
    }

    @Test
    void listReturnsAttachmentsForOwnedTransaction() {
        TransactionAttachment attachment = TransactionAttachment.builder()
                .id(20L)
                .transaction(transaction)
                .user(user)
                .fileName("receipt.pdf")
                .contentType("application/pdf")
                .fileSize(10L)
                .build();
        when(transactionRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(transaction));
        when(attachmentRepository.findByTransactionAndUserOrderByCreatedAtDesc(transaction, user))
                .thenReturn(List.of(attachment));

        List<AttachmentResponse> responses = attachmentService.list(10L, user);

        assertEquals(1, responses.size());
        assertEquals("receipt.pdf", responses.getFirst().getFileName());
    }

    @Test
    void getDownloadThrowsWhenStoredFileIsMissing() {
        TransactionAttachment attachment = TransactionAttachment.builder()
                .id(20L)
                .transaction(transaction)
                .user(user)
                .fileName("missing.pdf")
                .contentType("application/pdf")
                .fileSize(10L)
                .storagePath(uploadDir.resolve("missing.pdf").toString())
                .build();
        when(attachmentRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(attachment));

        assertThrows(ResourceNotFoundException.class, () -> attachmentService.getDownload(20L, user));
    }

    @Test
    void deleteRemovesStoredFileAndRecord() throws Exception {
        Path storedFile = Files.writeString(uploadDir.resolve("receipt.pdf"), "content");
        TransactionAttachment attachment = TransactionAttachment.builder()
                .id(20L)
                .transaction(transaction)
                .user(user)
                .fileName("receipt.pdf")
                .contentType("application/pdf")
                .fileSize(10L)
                .storagePath(storedFile.toString())
                .build();
        when(attachmentRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(attachment));

        attachmentService.delete(20L, user);

        assertFalse(Files.exists(storedFile));
        verify(attachmentRepository).delete(attachment);
    }
}
