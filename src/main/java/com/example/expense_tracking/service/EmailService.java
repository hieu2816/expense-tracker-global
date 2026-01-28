package com.example.expense_tracking.service;

import com.example.expense_tracking.repository.BankConfigRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final TransactionService transactionService;
    private final BankConfigRepository bankConfigRepository;

    @Value("${spring.mail.imap.host}")
    private String host;

    @Value("${spring.mail.imap.username}")
    private String username;

    @Value("${spring.mail.imap.password}")
    private String password;

    // Run every 60 seconds (60000 ms)
    @Scheduled(fixedDelay = 60000)
    public void checkEmails() {
        try {
            // 1. Connect to Gmail
            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "imaps");

            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect(host, username, password);

            // 2. Open Inbox
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE); // Read-Write to mark emails as SEEN

            // 3. Search for UNREAD emails only
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            if (messages.length > 0) {
                log.info("Found {} unread emails!", messages.length);
            };

            for (Message message : messages) {
                // Get the Subject
                String subject = message.getSubject();
                // Get the Sender
                String from = ((InternetAddress) message.getFrom()[0]).getAddress();
                String body = getTextFromMessage(message);

                log.info("Processing Email -> From: {} | Subject: {}", from, subject);

                // Google Verification Code
                if (from.contains("google.com") && subject.contains("Forwarding Confirmation")) {
                    extractAndLogVerificationCode(body);
                }

                // Regex Email From Bank Transaction
                else {
                    processBankEmail(body);
                }

                // 4. Mark as READ (So we don't process it again next time)
                message.setFlag(Flags.Flag.SEEN, true);
            }

            inbox.close(false);
            store.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // HELPER METHOD

    private void extractAndLogVerificationCode(String body) {
        // Regex to find the Google code (8-9 digits)
        Pattern pattern = Pattern.compile("\\b(\\d{8,})\\b");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String code = matcher.group(1);
            log.warn("GOOGLE VERIFICATION CODE FOUND: " + code);
            log.warn("GIVE THIS CODE TO YOUR USER TO ENABLE FORWARDING");
        }
    }

    private void processBankEmail(String body) {
        // Clean the body (remove HTML tags if any, simplify spaces)
        String cleanBody = body.replace("\\<.*?\\>", "").replaceAll("\\s+", " ");

        // A. Extract Account Number
        String accountNumber = extractValue(cleanBody, "TK ")
        // B. Find the owner in database
        // D. Extract Amount
        // D. Extract Type
        // E. Create Transaction

    }

    private String extractValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            return getTextFromMimeMultipart(mimeMultipart);
        }
        return "";
    }

    private String getTextFromMimeMultipart (MimeMultipart mimeMultipart) throws Exception {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent());
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result.append(org.jsoup.Jsoup.parse(html).text());
            }
        }
        return result.toString();
    }
}