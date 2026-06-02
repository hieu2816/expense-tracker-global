package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.*;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserInputControllerTest {
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
    }

    @Test
    void categoryRuleControllerDelegatesToService() {
        CategoryRuleService service = mock(CategoryRuleService.class);
        CategoryRuleController controller = new CategoryRuleController(service);
        CategoryRuleRequest request = new CategoryRuleRequest();
        CategoryRuleResponse response = CategoryRuleResponse.builder().id(1L).build();
        when(service.getRules(user)).thenReturn(List.of(response));
        when(service.createRule(request, user)).thenReturn(response);
        when(service.updateRule(1L, request, user)).thenReturn(response);

        assertEquals(1, controller.getRules(user).getBody().size());
        assertEquals(response, controller.createRule(user, request).getBody());
        assertEquals(response, controller.updateRule(user, 1L, request).getBody());
        assertEquals(204, controller.deleteRule(user, 1L).getStatusCode().value());
        verify(service).deleteRule(1L, user);
    }

    @Test
    void importControllerDelegatesToService() throws Exception {
        CsvImportService service = mock(CsvImportService.class);
        ImportController controller = new ImportController(service);
        CsvImportRequest request = new CsvImportRequest();
        UUID id = UUID.randomUUID();
        ImportBatchResponse batch = ImportBatchResponse.builder().id(id).build();
        when(service.preview(request, user)).thenReturn(CsvImportPreviewResponse.builder().build());
        when(service.commit(request, user)).thenReturn(CsvImportResultResponse.builder().build());
        when(service.getImportHistory(user)).thenReturn(List.of(batch));
        when(service.getImportBatch(id, user)).thenReturn(Optional.of(batch));

        assertNotNull(controller.previewCsv(user, request).getBody());
        assertNotNull(controller.commitCsv(user, request).getBody());
        assertEquals(1, controller.getImports(user).getBody().size());
        assertEquals(batch, controller.getImport(user, id).getBody());
    }

    @Test
    void naturalLanguageControllerDelegatesToService() {
        NaturalLanguageTransactionService service = mock(NaturalLanguageTransactionService.class);
        NaturalLanguageTransactionController controller = new NaturalLanguageTransactionController(service);
        NaturalLanguageParseRequest parseRequest = new NaturalLanguageParseRequest();
        NaturalLanguageConfirmRequest confirmRequest = new NaturalLanguageConfirmRequest();
        NaturalLanguageDraftResponse draft = NaturalLanguageDraftResponse.builder().originalText("lunch 12").build();
        TransactionResponse transaction = TransactionResponse.builder().id(1L).build();
        when(service.parse(parseRequest, user)).thenReturn(draft);
        when(service.confirm(confirmRequest, user)).thenReturn(transaction);

        assertEquals(draft, controller.parse(user, parseRequest).getBody());
        assertEquals(transaction, controller.confirm(user, confirmRequest).getBody());
    }

    @Test
    void templateControllerDelegatesToService() {
        TransactionTemplateService service = mock(TransactionTemplateService.class);
        TransactionTemplateController controller = new TransactionTemplateController(service);
        TransactionTemplateRequest request = new TransactionTemplateRequest();
        TransactionTemplateResponse template = TransactionTemplateResponse.builder().id(1L).build();
        TransactionResponse transaction = TransactionResponse.builder().id(2L).build();
        when(service.getTemplates(user)).thenReturn(List.of(template));
        when(service.createTemplate(request, user)).thenReturn(template);
        when(service.updateTemplate(1L, request, user)).thenReturn(template);
        when(service.createTransactionFromTemplate(1L, user)).thenReturn(transaction);

        assertEquals(1, controller.getTemplates(user).getBody().size());
        assertEquals(template, controller.createTemplate(user, request).getBody());
        assertEquals(template, controller.updateTemplate(user, 1L, request).getBody());
        assertEquals(transaction, controller.createTransactionFromTemplate(user, 1L).getBody());
        assertEquals(204, controller.deleteTemplate(user, 1L).getStatusCode().value());
        verify(service).deleteTemplate(1L, user);
    }

    @Test
    void recurringControllerDelegatesToService() {
        RecurringTransactionService service = mock(RecurringTransactionService.class);
        RecurringTransactionController controller = new RecurringTransactionController(service);
        RecurringTransactionRequest request = new RecurringTransactionRequest();
        RecurringTransactionResponse response = RecurringTransactionResponse.builder().id(1L).build();
        when(service.getRecurring(user)).thenReturn(List.of(response));
        when(service.createRecurring(request, user)).thenReturn(response);
        when(service.updateRecurring(1L, request, user)).thenReturn(response);

        assertEquals(1, controller.getRecurring(user).getBody().size());
        assertEquals(response, controller.createRecurring(user, request).getBody());
        assertEquals(response, controller.updateRecurring(user, 1L, request).getBody());
        assertEquals(204, controller.deleteRecurring(user, 1L).getStatusCode().value());
        verify(service).deleteRecurring(1L, user);
    }

    @Test
    void attachmentControllerDelegatesToService() throws Exception {
        AttachmentService service = mock(AttachmentService.class);
        AttachmentController controller = new AttachmentController(service);
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "x".getBytes());
        AttachmentResponse attachment = AttachmentResponse.builder().id(1L).fileName("receipt.pdf").build();
        ByteArrayResource resource = new ByteArrayResource("x".getBytes());
        when(service.upload(10L, file, user)).thenReturn(attachment);
        when(service.list(10L, user)).thenReturn(List.of(attachment));
        when(service.getDownload(1L, user)).thenReturn(new AttachmentService.AttachmentDownload("receipt.pdf", "application/pdf", resource));

        assertEquals(attachment, controller.uploadAttachment(user, 10L, file).getBody());
        assertEquals(1, controller.getAttachments(user, 10L).getBody().size());
        assertEquals(resource, controller.downloadAttachment(user, 1L).getBody());
        assertEquals(204, controller.deleteAttachment(user, 1L).getStatusCode().value());
        verify(service).delete(1L, user);
    }
}
