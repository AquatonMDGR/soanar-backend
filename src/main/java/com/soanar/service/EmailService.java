package com.soanar.service;

import com.soanar.model.Email;
import com.soanar.model.Announcement;
import com.soanar.model.OrganizationSettings;
import com.soanar.model.User;
import com.soanar.repository.EmailRepository;
import com.soanar.repository.UserRepository;
import com.soanar.repository.AnnouncementRepository;
import com.soanar.repository.NotificationPreferenceRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.io.IOException;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final OrganizationSettingsService organizationSettingsService;

    private static final String DEFAULT_TERM_1_START = "01-01";
    private static final String DEFAULT_TERM_1_END = "03-31";
    private static final String DEFAULT_TERM_2_START = "04-01";
    private static final String DEFAULT_TERM_2_END = "07-31";
    private static final String DEFAULT_TERM_3_START = "08-01";
    private static final String DEFAULT_TERM_3_END = "12-31";

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${MAIL_FROM:${MAIL_USERNAME:}}")
    private String mailFrom;

    @Value("${mail.provider:smtp}")
    private String mailProvider;

    @Value("${resend.api-key:${RESEND_API_KEY:}}")
    private String resendApiKey;

    @Value("${resend.api-url:${RESEND_API_URL:https://api.resend.com/emails}}")
    private String resendApiUrl;

    @Value("${sendgrid.api-key:${SENDGRID_API_KEY:}}")
    private String sendgridApiKey;

    @Value("${mail.send-timeout-ms:${MAIL_SEND_TIMEOUT_MS:10000}}")
    private int mailSendTimeoutMs;

    @Value("${mail.from-name:${MAIL_FROM_NAME:SOANAR}}")
    private String mailFromName;

    public EmailService(JavaMailSender mailSender,
                        EmailRepository emailRepository,
                        UserRepository userRepository,
                        AnnouncementRepository announcementRepository,
                        NotificationPreferenceRepository notificationPreferenceRepository,
                        OrganizationSettingsService organizationSettingsService) {
        this.mailSender = mailSender;
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.organizationSettingsService = organizationSettingsService;
    }

    public void sendTargetedEmail(List<String> recipients, String subject, String body) {
        for (String recipient : recipients) {
            try {
                deliverEmail(recipient, subject, body);

                Email email = new Email();
                email.setRecipientEmail(recipient);
                email.setSubject(subject);
                email.setBody(body);
                email.setStatus("SENT");
                email.setSentAt(Instant.now());
                emailRepository.save(email);
            } catch (Exception e) {
                logger.error("Email send failed for {}: {}", recipient, describeEmailFailure(e), e);
                Email email = new Email();
                email.setRecipientEmail(recipient);
                email.setSubject(subject);
                email.setBody(body);
                email.setStatus("FAILED");
                email.setSentAt(Instant.now());
                emailRepository.save(email);
            }
        }
    }

    private String deliverEmail(String recipient, String subject, String body) throws Exception {
        String provider = mailProvider != null ? mailProvider.trim().toLowerCase() : "smtp";
        boolean resendConfigured = resendApiKey != null && !resendApiKey.isBlank();

        if ("resend".equals(provider)) {
            sendViaResend(recipient, subject, body);
            return "RESEND";
        }

        if ("sendgrid".equals(provider)) {
            sendViaSendGrid(recipient, subject, body);
            return "SENDGRID";
        }

        if ("auto".equals(provider)) {
            try {
                sendViaSmtp(recipient, subject, body);
                return "SMTP";
            } catch (Exception smtpEx) {
                if (!resendConfigured) {
                    throw smtpEx;
                }
                logger.warn("SMTP delivery failed for {}. Retrying with Resend. Reason: {}", recipient, describeEmailFailure(smtpEx));
                sendViaResend(recipient, subject, body);
                return "RESEND";
            }
        }

        sendViaSmtp(recipient, subject, body);
        return "SMTP";
    }

    private void sendViaSmtp(String recipient, String subject, String body) throws Exception {
        String modifiedBody = body != null ? body : "";
        Pattern imgPattern = Pattern.compile("<img[^>]+src=[\"'](https?://[^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = imgPattern.matcher(modifiedBody);
        List<String> urls = new ArrayList<>();
        while (matcher.find()) {
            String url = matcher.group(1);
            if (!urls.contains(url)) urls.add(url);
        }

        Map<String, byte[]> cidToBytes = new HashMap<>();
        Map<String, String> cidToContentType = new HashMap<>();

        if (!urls.isEmpty()) {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            int i = 0;
            for (String url : urls) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                    HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        byte[] bytes = resp.body();
                        String contentType = resp.headers().firstValue("content-type").orElse("image/png");
                        String cid = "img" + i;
                        cidToBytes.put(cid, bytes);
                        cidToContentType.put(cid, contentType);
                        modifiedBody = modifiedBody.replace(url, "cid:" + cid);
                        i++;
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to download inline image {}: {}", url, ex.getMessage());
                }
            }
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        if (mailFrom != null && !mailFrom.isBlank()) {
            helper.setFrom(mailFrom);
        }
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(modifiedBody, true);

        for (Map.Entry<String, byte[]> e : cidToBytes.entrySet()) {
            String cid = e.getKey();
            byte[] bytes = e.getValue();
            String ct = cidToContentType.getOrDefault(cid, "image/png");
            ByteArrayResource resource = new ByteArrayResource(bytes);
            try {
                helper.addInline(cid, resource, ct);
            } catch (Exception ex) {
                logger.warn("Failed to attach inline image {}: {}", cid, ex.getMessage());
            }
        }

        mailSender.send(mimeMessage);
    }

    private String describeEmailFailure(Exception exception) {
        if (isConnectTimeout(exception)) {
            return "SMTP connection timed out while connecting to the mail host. This usually means outbound SMTP is blocked or the host is unreachable.";
        }

        if (findCause(exception, MailAuthenticationException.class) != null) {
            return "SMTP authentication failed. Check MAIL_USERNAME, MAIL_PASSWORD, and Gmail app-password settings.";
        }

        Throwable socketTimeout = findCauseByClassName(exception, "SocketTimeoutException");
        if (socketTimeout != null) {
            return "SMTP socket timed out after connecting. Check firewall/network conditions and SMTP timeout values.";
        }

        Throwable unknownHost = findCauseByClassName(exception, "UnknownHostException");
        if (unknownHost != null) {
            return "Mail host could not be resolved. Check MAIL_HOST and DNS/network configuration.";
        }

        String message = exception.getMessage();
        return message != null && !message.isBlank() ? message : exception.getClass().getSimpleName();
    }

    private boolean isConnectTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (current.getClass().getSimpleName().equals("MailConnectException")) {
                return true;
            }
            if (message != null && message.toLowerCase().contains("connect timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private Throwable findCauseByClassName(Throwable throwable, String simpleClassName) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass().getSimpleName().equals(simpleClassName)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void sendViaResend(String recipient, String subject, String body) throws IOException, InterruptedException {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured");
        }
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("MAIL_FROM (or MAIL_USERNAME) is required for Resend sender");
        }

        String from = (mailFromName != null && !mailFromName.isBlank())
                ? mailFromName + " <" + mailFrom + ">"
                : mailFrom;

        String html = body != null ? body : "";
        String payload = "{"
                + "\"from\":\"" + jsonEscape(from) + "\"," 
                + "\"to\":[\"" + jsonEscape(recipient) + "\"],"
                + "\"subject\":\"" + jsonEscape(subject != null ? subject : "") + "\"," 
                + "\"html\":\"" + jsonEscape(html) + "\""
                + "}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(mailSendTimeoutMs, 1000)))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resendApiUrl))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(Math.max(mailSendTimeoutMs, 1000)))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Resend API failed (" + response.statusCode() + "): " + response.body());
        }
    }

    private void sendViaSendGrid(String recipient, String subject, String body) throws IOException, InterruptedException {
        if (sendgridApiKey == null || sendgridApiKey.isBlank()) {
            throw new IllegalStateException("SENDGRID_API_KEY is not configured");
        }
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("MAIL_FROM (or MAIL_USERNAME) is required as the SendGrid sender address");
        }

        String fromName = (mailFromName != null && !mailFromName.isBlank()) ? mailFromName : mailFrom;
        String html = body != null ? body : "";

        // SendGrid v3 Mail Send API — uses HTTPS on port 443, bypasses SMTP port blocking
        String payload = "{"
                + "\"personalizations\":[{\"to\":[{\"email\":\"" + jsonEscape(recipient) + "\"}]}],"
                + "\"from\":{\"email\":\"" + jsonEscape(mailFrom) + "\",\"name\":\"" + jsonEscape(fromName) + "\"},"
                + "\"subject\":\"" + jsonEscape(subject != null ? subject : "") + "\","
                + "\"content\":[{\"type\":\"text/html\",\"value\":\"" + jsonEscape(html) + "\"}]"
                + "}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(mailSendTimeoutMs, 1000)))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Authorization", "Bearer " + sendgridApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(Math.max(mailSendTimeoutMs, 1000)))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // SendGrid returns 202 Accepted on success
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("SendGrid API failed (" + response.statusCode() + "): " + response.body());
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void sendTermlyNewsletter(List<String> recipients, String subject, String body) {
        sendTargetedEmail(recipients, subject, body);
    }

    public Map<String, Object> getTermCoverageSettings() {
        List<TermDefinition> terms = getConfiguredTerms();
        return Map.of(
                "term1", Map.of("start", formatMonthDay(terms.get(0).start()), "end", formatMonthDay(terms.get(0).end())),
                "term2", Map.of("start", formatMonthDay(terms.get(1).start()), "end", formatMonthDay(terms.get(1).end())),
                "term3", Map.of("start", formatMonthDay(terms.get(2).start()), "end", formatMonthDay(terms.get(2).end()))
        );
    }

    public Map<String, Object> updateTermCoverageSettings(Map<String, String> payload) {
        MonthDay term1Start = parseMonthDayRequired(payload.get("term1Start"), "term1Start");
        MonthDay term1End = parseMonthDayRequired(payload.get("term1End"), "term1End");
        MonthDay term2Start = parseMonthDayRequired(payload.get("term2Start"), "term2Start");
        MonthDay term2End = parseMonthDayRequired(payload.get("term2End"), "term2End");
        MonthDay term3Start = parseMonthDayRequired(payload.get("term3Start"), "term3Start");
        MonthDay term3End = parseMonthDayRequired(payload.get("term3End"), "term3End");

        List<TermDefinition> terms = List.of(
                new TermDefinition(1, term1Start, term1End),
                new TermDefinition(2, term2Start, term2End),
                new TermDefinition(3, term3Start, term3End)
        );
        validateNonOverlappingTerms(terms);

        OrganizationSettings settings = organizationSettingsService.getOrCreateDefaultSettings();
        settings.setTerm1StartMonthDay(formatMonthDay(term1Start));
        settings.setTerm1EndMonthDay(formatMonthDay(term1End));
        settings.setTerm2StartMonthDay(formatMonthDay(term2Start));
        settings.setTerm2EndMonthDay(formatMonthDay(term2End));
        settings.setTerm3StartMonthDay(formatMonthDay(term3Start));
        settings.setTerm3EndMonthDay(formatMonthDay(term3End));
        organizationSettingsService.createOrUpdateSettings(settings);

        return getTermCoverageSettings();
    }

    public Map<String, Object> forceSendUpcomingTermNewsletter() {
        ConfiguredTermWindow nextWindow = resolveNextConfiguredTermWindow(LocalDate.now());
        LocalDate termStart = nextWindow.start();
        LocalDate termEnd = nextWindow.end();
        String termLabel = "Term " + nextWindow.termNumber();
        String organizationId = organizationSettingsService.resolveDefaultOrganizationId();

        List<Announcement> upcoming = announcementRepository.findPublishedInDateRange(
                Arrays.asList("PUBLISHED", "APPROVED"),
                termStart,
                termEnd
        );

        List<String> recipients = userRepository.findByRoleAndIsActiveTrue("Student").stream()
            .filter(user -> isEmailEnabledForUser(user, organizationId))
            .map(User::getSchoolEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        if (!upcoming.isEmpty() && !recipients.isEmpty()) {
            String subject = "SOANAR Upcoming Events for " + termLabel + " (" + termStart + " to " + termEnd + ")";
            String body = buildUpcomingTermNewsletterHtml(upcoming, termStart, termEnd, termLabel);
            sendTermlyNewsletter(recipients, subject, body);
        }

        boolean sent = !upcoming.isEmpty() && !recipients.isEmpty();
        String message;
        if (sent) {
            message = "Upcoming termly email sent successfully.";
        } else if (upcoming.isEmpty()) {
            message = "No upcoming published events found for the next term window.";
        } else {
            message = "No student recipients found for termly email.";
        }

        return Map.of(
                "message", message,
                "termLabel", termLabel,
                "termStart", termStart.toString(),
                "termEnd", termEnd.toString(),
                "eventsIncluded", upcoming.size(),
                "recipients", recipients.size(),
                "sent", sent
        );
    }

    public Map<String, Object> getUpcomingTermNewsletterPreview() {
        ConfiguredTermWindow nextWindow = resolveNextConfiguredTermWindow(LocalDate.now());
        LocalDate termStart = nextWindow.start();
        LocalDate termEnd = nextWindow.end();
        String termLabel = "Term " + nextWindow.termNumber();
        String organizationId = organizationSettingsService.resolveDefaultOrganizationId();

        List<Announcement> upcoming = announcementRepository.findPublishedInDateRange(
                Arrays.asList("PUBLISHED", "APPROVED"),
                termStart,
                termEnd
        );

        List<String> recipients = userRepository.findByRoleAndIsActiveTrue("Student").stream()
            .filter(user -> isEmailEnabledForUser(user, organizationId))
            .map(User::getSchoolEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        boolean hasContent = !upcoming.isEmpty() && !recipients.isEmpty();
        String message;
        if (hasContent) {
            message = "Preview ready.";
        } else if (upcoming.isEmpty()) {
            message = "No upcoming published events found for the next term window.";
        } else {
            message = "No student recipients found for termly email.";
        }

        String subject = "SOANAR Upcoming Events for " + termLabel + " (" + termStart + " to " + termEnd + ")";
        String htmlContent = hasContent ? buildUpcomingTermNewsletterHtml(upcoming, termStart, termEnd, termLabel) : "";

        return Map.of(
                "message", message,
                "subject", subject,
                "htmlContent", htmlContent,
                "termLabel", termLabel,
                "termStart", termStart.toString(),
                "termEnd", termEnd.toString(),
                "eventsIncluded", upcoming.size(),
                "recipients", recipients.size(),
                "hasContent", hasContent
        );
    }

    private List<TermDefinition> getConfiguredTerms() {
        OrganizationSettings settings = organizationSettingsService.getOrCreateDefaultSettings();

        TermDefinition term1 = new TermDefinition(
                1,
                parseMonthDayOrDefault(settings.getTerm1StartMonthDay(), DEFAULT_TERM_1_START),
                parseMonthDayOrDefault(settings.getTerm1EndMonthDay(), DEFAULT_TERM_1_END)
        );
        TermDefinition term2 = new TermDefinition(
                2,
                parseMonthDayOrDefault(settings.getTerm2StartMonthDay(), DEFAULT_TERM_2_START),
                parseMonthDayOrDefault(settings.getTerm2EndMonthDay(), DEFAULT_TERM_2_END)
        );
        TermDefinition term3 = new TermDefinition(
                3,
                parseMonthDayOrDefault(settings.getTerm3StartMonthDay(), DEFAULT_TERM_3_START),
                parseMonthDayOrDefault(settings.getTerm3EndMonthDay(), DEFAULT_TERM_3_END)
        );

        List<TermDefinition> terms = List.of(term1, term2, term3);
        validateNonOverlappingTerms(terms);
        return terms;
    }

    private ConfiguredTermWindow resolveNextConfiguredTermWindow(LocalDate referenceDate) {
        List<TermDefinition> terms = getConfiguredTerms();
        List<ConfiguredTermWindow> thisYearWindows = terms.stream()
                .map(term -> new ConfiguredTermWindow(
                        term.termNumber(),
                        term.start().atYear(referenceDate.getYear()),
                        term.end().atYear(referenceDate.getYear())
                ))
                .sorted(Comparator.comparing(ConfiguredTermWindow::start))
                .toList();

        for (int i = 0; i < thisYearWindows.size(); i++) {
            ConfiguredTermWindow window = thisYearWindows.get(i);
            if (!referenceDate.isBefore(window.start()) && !referenceDate.isAfter(window.end())) {
                if (i + 1 < thisYearWindows.size()) {
                    return thisYearWindows.get(i + 1);
                }
                TermDefinition firstTerm = terms.get(0);
                return new ConfiguredTermWindow(
                        firstTerm.termNumber(),
                        firstTerm.start().atYear(referenceDate.getYear() + 1),
                        firstTerm.end().atYear(referenceDate.getYear() + 1)
                );
            }
        }

        for (ConfiguredTermWindow window : thisYearWindows) {
            if (window.start().isAfter(referenceDate)) {
                return window;
            }
        }

        TermDefinition firstTerm = terms.get(0);
        return new ConfiguredTermWindow(
                firstTerm.termNumber(),
                firstTerm.start().atYear(referenceDate.getYear() + 1),
                firstTerm.end().atYear(referenceDate.getYear() + 1)
        );
    }

    private void validateNonOverlappingTerms(List<TermDefinition> terms) {
        List<ConfiguredTermWindow> sorted = terms.stream()
                .map(term -> new ConfiguredTermWindow(
                        term.termNumber(),
                        term.start().atYear(2000),
                        term.end().atYear(2000)
                ))
                .sorted(Comparator.comparing(ConfiguredTermWindow::start))
                .toList();

        for (ConfiguredTermWindow term : sorted) {
            if (term.end().isBefore(term.start())) {
                throw new IllegalArgumentException("Each term must have an end date on or after its start date.");
            }
        }

        for (int i = 1; i < sorted.size(); i++) {
            ConfiguredTermWindow previous = sorted.get(i - 1);
            ConfiguredTermWindow current = sorted.get(i);
            if (!current.start().isAfter(previous.end())) {
                throw new IllegalArgumentException("Term coverage dates must not overlap.");
            }
        }
    }

    private MonthDay parseMonthDayRequired(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required. Use MM-dd format.");
        }
        return parseMonthDay(value);
    }

    private MonthDay parseMonthDayOrDefault(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return parseMonthDay(fallback);
        }
        return parseMonthDay(normalized);
    }

    private MonthDay parseMonthDay(String value) {
        try {
            String normalized = value.trim();
            if (!normalized.matches("^\\d{2}-\\d{2}$")) {
                throw new IllegalArgumentException("Invalid Month-Day format. Use MM-dd (example: 08-01).");
            }
            return MonthDay.parse("--" + normalized);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid Month-Day format. Use MM-dd (example: 08-01).", ex);
        }
    }

    private String formatMonthDay(MonthDay monthDay) {
        return String.format("%02d-%02d", monthDay.getMonthValue(), monthDay.getDayOfMonth());
    }

    private record TermDefinition(int termNumber, MonthDay start, MonthDay end) {}

    private record ConfiguredTermWindow(int termNumber, LocalDate start, LocalDate end) {}

    private String buildUpcomingTermNewsletterHtml(List<Announcement> upcoming, LocalDate start, LocalDate end, String termLabel) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"margin:0;padding:0;background:#f3f2ef;font-family:Arial,Helvetica,sans-serif;\">");
        html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f2ef;padding:24px 0;\">");
        html.append("<tr><td align=\"center\">");
        html.append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;background:#ffffff;border:1px solid #d1d5db;border-radius:12px;overflow:hidden;\">");

        html.append("<tr><td style=\"padding:18px 24px;background:#0a66c2;color:#ffffff;font-size:15px;font-weight:700;\">SOANAR Updates</td></tr>");
        html.append("<tr><td style=\"padding:24px 24px 8px;color:#111827;font-size:34px;line-height:1.2;font-weight:700;\">Upcoming Events for ")
            .append(escapeHtml(termLabel))
            .append("</td></tr>");
        html.append("<tr><td style=\"padding:0 24px 18px;color:#4b5563;font-size:15px;\"><strong>Coverage:</strong> ")
            .append(start)
            .append(" to ")
            .append(end)
            .append("</td></tr>");

        for (Announcement announcement : upcoming) {
            html.append("<tr><td style=\"padding:0 24px 12px;\">");
            html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;\">");
            html.append("<tr><td style=\"padding:14px 16px 6px;color:#111827;font-size:18px;font-weight:700;\">")
                .append(escapeHtml(resolveEmailEventTitle(announcement)))
                .append("</td></tr>");
            html.append("<tr><td style=\"padding:0 16px 8px;color:#374151;font-size:14px;\">Date: ")
                .append(formatAnnouncementDateRange(announcement))
                .append("</td></tr>");
            html.append("<tr><td style=\"padding:0 16px 14px;color:#4b5563;font-size:14px;line-height:1.5;\">")
                .append(nl2br(escapeHtml(announcement.getDescription())))
                .append("</td></tr>");
            html.append("</table>");
            html.append("</td></tr>");
        }

        String resolvedFrontendUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:3000";
        if (resolvedFrontendUrl.endsWith("/")) {
            resolvedFrontendUrl = resolvedFrontendUrl.substring(0, resolvedFrontendUrl.length() - 1);
        }

        html.append("<tr><td align=\"center\" style=\"padding:16px 24px 8px;\">");
        html.append("<a href=\"").append(escapeHtml(resolvedFrontendUrl)).append("\" style=\"display:inline-block;background:#0a66c2;color:#ffffff;text-decoration:none;font-size:16px;font-weight:700;padding:12px 24px;border-radius:999px;\">Open SOANAR</a>");
        html.append("</td></tr>");
        html.append("<tr><td style=\"padding:8px 24px 24px;color:#6b7280;font-size:13px;line-height:1.5;text-align:center;\">You are receiving this update because you are enrolled in SOANAR notifications.</td></tr>");

        html.append("</table>");
        html.append("</td></tr></table>");
        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String nl2br(String value) {
        return value == null ? "" : value.replace("\n", "<br/>");
    }

    private String formatAnnouncementDateRange(Announcement announcement) {
        LocalDate startDate = announcement.getStartDate();
        LocalDate endDate = announcement.getEndDate();

        if (startDate == null && endDate == null) {
            return "TBD";
        }
        if (startDate != null && (endDate == null || endDate.equals(startDate))) {
            return startDate.toString();
        }
        if (startDate == null) {
            return endDate.toString();
        }
        return startDate + " to " + endDate;
    }

    private String resolveEmailEventTitle(Announcement announcement) {
        String title = announcement.getTitle() != null ? announcement.getTitle().trim() : "";
        boolean looksAutoGenerated = title.endsWith("....") || title.endsWith("...");
        if (!title.isBlank() && !"event".equalsIgnoreCase(title) && !looksAutoGenerated) {
            return title;
        }

        String description = announcement.getDescription() != null ? announcement.getDescription().trim() : "";
        if (!description.isBlank()) {
            String firstLine = description.split("\\R", 2)[0].trim();
            if (!firstLine.isBlank()) {
                return firstLine.length() > 80 ? firstLine.substring(0, 80) + "..." : firstLine;
            }
        }

        return "Announcement";
    }

    private boolean isEmailEnabledForUser(User user, String organizationId) {
        return notificationPreferenceRepository.findByUserAndOrganizationId(user, organizationId)
                .map(preference -> preference.getNotifyByEmail() == null || preference.getNotifyByEmail())
                .orElse(true);
    }
}
