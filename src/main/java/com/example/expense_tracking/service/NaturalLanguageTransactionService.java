package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.NaturalLanguageConfirmRequest;
import com.example.expense_tracking.dto.NaturalLanguageDraftResponse;
import com.example.expense_tracking.dto.NaturalLanguageParseRequest;
import com.example.expense_tracking.dto.TransactionResponse;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.TransactionSource;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NaturalLanguageTransactionService {
    private static final Pattern EN_AMOUNT = Pattern.compile("\\$?\\b(\\d+(?:[.,]\\d+)?)(k)?\\b");

    private final CategoryRuleService categoryRuleService;
    private final TransactionService transactionService;

    public NaturalLanguageDraftResponse parse(NaturalLanguageParseRequest request, User user) {
        return parseEnglish(request.getText(), user);
    }

    public TransactionResponse confirm(NaturalLanguageConfirmRequest request, User user) {
        TransactionSource source = TransactionSource.NATURAL_LANGUAGE_EN;
        String sourceReference = source.name() + "_" + sha256(user.getId() + "|" + request.getOriginalText() + "|"
                + request.getTransaction().getAmount() + "|" + request.getTransaction().getTransactionDate());
        return transactionService.createTransactionFromSource(request.getTransaction(), user, source, sourceReference,
                null, request.getOriginalText(), request.getConfidence());
    }

    private NaturalLanguageDraftResponse parseEnglish(String text, User user) {
        String normalized = normalize(text);
        BigDecimal amount = extractEnglishAmount(normalized);
        LocalDateTime date = extractEnglishDate(normalized);
        TransactionType type = inferEnglishType(normalized);
        String description = cleanupDescription(text);
        String category = categoryRuleService.findMatchingCategory(description, type, user)
                .map(Category::getName)
                .orElseGet(() -> inferEnglishCategory(normalized, type));
        return buildDraft(text, "en", description, amount, type, category, date);
    }

    private NaturalLanguageDraftResponse buildDraft(String original, String language, String description,
            BigDecimal amount, TransactionType type, String category, LocalDateTime date) {
        List<String> warnings = new ArrayList<>();
        BigDecimal confidence = BigDecimal.ZERO;
        if (amount != null) {
            confidence = confidence.add(new BigDecimal("0.35"));
        } else {
            warnings.add("Amount could not be parsed");
        }
        if (date != null) {
            confidence = confidence.add(new BigDecimal("0.20"));
        } else {
            date = LocalDate.now().atStartOfDay();
            warnings.add("Date was not found, defaulted to today");
        }
        if (category != null && !category.isBlank()) {
            confidence = confidence.add(new BigDecimal("0.20"));
        } else {
            category = "Uncategorized";
            warnings.add("Category could not be inferred");
        }
        if (type != null) {
            confidence = confidence.add(new BigDecimal("0.15"));
        } else {
            type = TransactionType.OUT;
            warnings.add("Type was not found, defaulted to expense");
        }
        if (description != null && !description.isBlank()) {
            confidence = confidence.add(new BigDecimal("0.10"));
        }

        return NaturalLanguageDraftResponse.builder()
                .originalText(original)
                .language(language)
                .description(description)
                .amount(amount)
                .type(type)
                .category(category)
                .transactionDate(date)
                .confidence(confidence)
                .warnings(warnings)
                .build();
    }

    private BigDecimal extractEnglishAmount(String normalized) {
        Matcher matcher = EN_AMOUNT.matcher(normalized);
        BigDecimal best = null;
        while (matcher.find()) {
            BigDecimal value = parseDecimal(matcher.group(1));
            if (value == null) {
                continue;
            }
            if ("k".equals(matcher.group(2))) {
                value = value.multiply(new BigDecimal("1000"));
            }
            if (best == null || value.compareTo(best) > 0) {
                best = value;
            }
        }
        return best;
    }

    private BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime extractEnglishDate(String normalized) {
        LocalDate today = LocalDate.now();
        if (normalized.contains("yesterday") || normalized.contains("last night")) {
            return today.minusDays(1).atStartOfDay();
        }
        if (normalized.contains("today") || normalized.contains("this morning")) {
            return today.atStartOfDay();
        }
        Matcher dayMatcher = Pattern.compile("(?:on\\s+the\\s+|on\\s+)?(\\d{1,2})(?:st|nd|rd|th)").matcher(normalized);
        if (dayMatcher.find()) {
            return withDay(today, Integer.parseInt(dayMatcher.group(1))).atStartOfDay();
        }
        Matcher slashMatcher = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b").matcher(normalized);
        if (slashMatcher.find()) {
            int month = Integer.parseInt(slashMatcher.group(1));
            int day = Integer.parseInt(slashMatcher.group(2));
            int year = slashMatcher.group(3) != null ? Integer.parseInt(slashMatcher.group(3)) : today.getYear();
            return LocalDate.of(year, month, day).atStartOfDay();
        }
        if (normalized.contains("last friday")) {
            return previous(DayOfWeek.FRIDAY).atStartOfDay();
        }
        return today.atStartOfDay();
    }

    private LocalDate withDay(LocalDate base, int day) {
        int safeDay = Math.max(1, Math.min(day, base.lengthOfMonth()));
        return base.withDayOfMonth(safeDay);
    }

    private LocalDate previous(DayOfWeek dayOfWeek) {
        LocalDate date = LocalDate.now().minusDays(1);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.minusDays(1);
        }
        return date;
    }

    private TransactionType inferEnglishType(String normalized) {
        if (containsAny(normalized, "salary", "paycheck", "paid", "refund", "income")) {
            return TransactionType.IN;
        }
        return TransactionType.OUT;
    }

    private String inferEnglishCategory(String normalized, TransactionType type) {
        if (type == TransactionType.IN) {
            return "Income";
        }
        if (containsAny(normalized, "grab", "uber", "taxi", "bus", "gas")) {
            return "Transport";
        }
        if (containsAny(normalized, "lunch", "dinner", "coffee", "restaurant")) {
            return "Food";
        }
        if (containsAny(normalized, "netflix", "spotify")) {
            return "Subscription";
        }
        if (containsAny(normalized, "hospital", "pharmacy")) {
            return "Health";
        }
        return "Uncategorized";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String cleanupDescription(String text) {
        return text.replaceAll("\\$?\\b\\d+(?:[.,]\\d+)?\\s*k?\\b", "")
                .replaceAll("(?i)\\b(today|yesterday|this morning|last night|on the \\d{1,2}(st|nd|rd|th))\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
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
}
