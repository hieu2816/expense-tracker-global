package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.*;
import com.example.expense_tracking.entity.*;
import com.example.expense_tracking.repository.ImportBatchRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CsvImportService {
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    private final TransactionRepository transactionRepository;
    private final ImportBatchRepository importBatchRepository;
    private final TransactionService transactionService;
    private final CategoryRuleService categoryRuleService;

    public CsvImportPreviewResponse preview(CsvImportRequest request, User user) throws IOException {
        ParsedCsv parsedCsv = parse(request, user);
        return CsvImportPreviewResponse.builder()
                .headers(parsedCsv.headers())
                .rows(parsedCsv.rows())
                .totalRows(parsedCsv.rows().size())
                .validRows((int) parsedCsv.rows().stream().filter(row -> row.getValid() && !row.getDuplicate()).count())
                .duplicateRows((int) parsedCsv.rows().stream().filter(CsvImportRowPreview::getDuplicate).count())
                .invalidRows((int) parsedCsv.rows().stream().filter(row -> !row.getValid()).count())
                .build();
    }

    @Transactional
    public CsvImportResultResponse commit(CsvImportRequest request, User user) throws IOException {
        ParsedCsv parsedCsv = parse(request, user);
        UUID batchId = UUID.randomUUID();
        int imported = 0;

        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .id(batchId)
                .user(user)
                .source(TransactionSource.CSV_IMPORT)
                .fileName(request.getFileName())
                .status(ImportStatus.PREVIEWED)
                .totalRows(parsedCsv.rows().size())
                .importedRows(0)
                .duplicateRows((int) parsedCsv.rows().stream().filter(CsvImportRowPreview::getDuplicate).count())
                .invalidRows((int) parsedCsv.rows().stream().filter(row -> !row.getValid()).count())
                .build());

        for (CsvImportRowPreview row : parsedCsv.rows()) {
            if (!row.getValid() || row.getDuplicate()) {
                continue;
            }
            String sourceReference = buildSourceReference(user.getId(), row.getTransactionDate(), row.getAmount(),
                    row.getDescription());
            TransactionRequest txRequest = new TransactionRequest();
            txRequest.setCategory(row.getCategory());
            txRequest.setAmount(row.getAmount());
            txRequest.setType(row.getType());
            txRequest.setDescription(row.getDescription());
            txRequest.setTransactionDate(row.getTransactionDate());
            transactionService.createTransactionFromSource(txRequest, user, TransactionSource.CSV_IMPORT,
                    sourceReference, batchId, null, null);
            imported++;
        }

        batch.setStatus(ImportStatus.COMPLETED);
        batch.setImportedRows(imported);
        batch.setCompletedAt(LocalDateTime.now());
        importBatchRepository.save(batch);

        return CsvImportResultResponse.builder()
                .importBatchId(batchId)
                .totalRows(batch.getTotalRows())
                .importedRows(imported)
                .duplicateRows(batch.getDuplicateRows())
                .invalidRows(batch.getInvalidRows())
                .build();
    }

    public List<ImportBatchResponse> getImportHistory(User user) {
        return importBatchRepository.findTop20ByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapBatch)
                .toList();
    }

    public Optional<ImportBatchResponse> getImportBatch(UUID id, User user) {
        return importBatchRepository.findByIdAndUser(id, user).map(this::mapBatch);
    }

    private ParsedCsv parse(CsvImportRequest request, User user) throws IOException {
        try (CSVParser parser = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreSurroundingSpaces()
                .parse(new StringReader(request.getContent()))) {
            List<String> headers = parser.getHeaderNames();
            List<CsvImportRowPreview> rows = new ArrayList<>();
            Set<String> seenInFile = new HashSet<>();
            for (CSVRecord record : parser) {
                CsvImportRowPreview row = parseRecord(record, request.getMapping(), user);
                String fileKey = row.getTransactionDate() + "|" + row.getAmount() + "|"
                        + (row.getDescription() == null ? "" : row.getDescription().trim().toLowerCase());
                if (row.getValid() && !seenInFile.add(fileKey)) {
                    row.setDuplicate(true);
                }
                rows.add(row);
            }
            return new ParsedCsv(headers, rows);
        }
    }

    private CsvImportRowPreview parseRecord(CSVRecord record, CsvColumnMapping mapping, User user) {
        List<String> errors = new ArrayList<>();
        LocalDateTime date = parseDate(get(record, mapping.getDate()), errors);
        String description = get(record, mapping.getDescription());
        BigDecimal signedAmount = parseAmount(get(record, mapping.getAmount()), errors);
        TransactionType type = parseType(get(record, mapping.getType()), signedAmount);
        BigDecimal amount = signedAmount != null ? signedAmount.abs() : null;

        if (description == null || description.isBlank()) {
            errors.add("Description is required");
        }
        if (type == null) {
            errors.add("Type could not be inferred");
        }
        String category = get(record, mapping.getCategory());
        if ((category == null || category.isBlank()) && type != null) {
            category = categoryRuleService.findMatchingCategory(description, type, user)
                    .map(Category::getName)
                    .orElse("Imported");
        }
        String currency = get(record, mapping.getCurrency());
        String sourceReference = date != null && amount != null
                ? buildSourceReference(user.getId(), date, amount, description)
                : null;
        boolean duplicate = sourceReference != null && transactionRepository.existsByUserAndSourceReference(user,
                sourceReference);

        return CsvImportRowPreview.builder()
                .rowNumber((int) record.getRecordNumber() + 1)
                .transactionDate(date)
                .description(description)
                .amount(amount)
                .type(type)
                .category(category)
                .currency(currency != null && !currency.isBlank() ? currency : "GBP")
                .valid(errors.isEmpty())
                .duplicate(duplicate)
                .errors(errors)
                .build();
    }

    private String get(CSVRecord record, String header) {
        if (header == null || header.isBlank() || !record.isMapped(header)) {
            return null;
        }
        String value = record.get(header);
        return value != null ? value.trim() : null;
    }

    private LocalDateTime parseDate(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add("Date is required");
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
                    return LocalDateTime.parse(raw, formatter);
                }
                return LocalDate.parse(raw, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }
        errors.add("Invalid date: " + raw);
        return null;
    }

    private BigDecimal parseAmount(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add("Amount is required");
            return null;
        }
        try {
            String normalized = raw.replace(",", "").replace("£", "").replace("$", "").trim();
            BigDecimal value = new BigDecimal(normalized);
            if (value.compareTo(BigDecimal.ZERO) == 0) {
                errors.add("Amount must not be zero");
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add("Invalid amount: " + raw);
            return null;
        }
    }

    private TransactionType parseType(String raw, BigDecimal signedAmount) {
        if (raw != null && !raw.isBlank()) {
            String normalized = raw.trim().toUpperCase();
            if (List.of("IN", "INCOME", "CREDIT").contains(normalized)) {
                return TransactionType.IN;
            }
            if (List.of("OUT", "EXPENSE", "DEBIT").contains(normalized)) {
                return TransactionType.OUT;
            }
        }
        if (signedAmount == null) {
            return null;
        }
        return signedAmount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.IN : TransactionType.OUT;
    }

    private String buildSourceReference(Long userId, LocalDateTime date, BigDecimal amount, String description) {
        String input = userId + "|" + date.toLocalDate() + "|" + amount.stripTrailingZeros().toPlainString() + "|"
                + (description == null ? "" : description.trim().toLowerCase());
        return "CSV_" + sha256(input);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private ImportBatchResponse mapBatch(ImportBatch batch) {
        return ImportBatchResponse.builder()
                .id(batch.getId())
                .source(batch.getSource())
                .fileName(batch.getFileName())
                .status(batch.getStatus())
                .totalRows(batch.getTotalRows())
                .importedRows(batch.getImportedRows())
                .duplicateRows(batch.getDuplicateRows())
                .invalidRows(batch.getInvalidRows())
                .createdAt(batch.getCreatedAt())
                .completedAt(batch.getCompletedAt())
                .build();
    }

    private record ParsedCsv(List<String> headers, List<CsvImportRowPreview> rows) {
    }
}
