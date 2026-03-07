package com.soanar.service;

import com.soanar.model.Email;
import com.soanar.model.Announcement;
import com.soanar.repository.EmailRepository;
import com.soanar.repository.UserRepository;
import com.soanar.repository.AnnouncementRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${MAIL_FROM:${MAIL_USERNAME:}}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender,
                        EmailRepository emailRepository,
                        UserRepository userRepository,
                        AnnouncementRepository announcementRepository) {
        this.mailSender = mailSender;
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
    }

    public void sendTargetedEmail(List<String> recipients, String subject, String body) {
        for (String recipient : recipients) {
            try {
                // Detect remote image URLs in the HTML body
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
                                // replace occurrences of the URL in the body with cid reference
                                modifiedBody = modifiedBody.replace(url, "cid:" + cid);
                                i++;
                            }
                        } catch (Exception ex) {
                            // If download fails, skip and leave original URL
                            System.err.println("Warning: failed to download inline image " + url + ": " + ex.getMessage());
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
                // Use modifiedBody (with cid: references) as HTML
                helper.setText(modifiedBody, true);

                // Attach any downloaded images as inline resources
                for (Map.Entry<String, byte[]> e : cidToBytes.entrySet()) {
                    String cid = e.getKey();
                    byte[] bytes = e.getValue();
                    String ct = cidToContentType.getOrDefault(cid, "image/png");
                    ByteArrayResource resource = new ByteArrayResource(bytes);
                    try {
                        helper.addInline(cid, resource, ct);
                    } catch (Exception ex) {
                        System.err.println("Warning: failed to attach inline image cid=" + cid + ": " + ex.getMessage());
                    }
                }

                mailSender.send(mimeMessage);

                Email email = new Email();
                email.setRecipientEmail(recipient);
                email.setSubject(subject);
                email.setBody(body);
                email.setStatus("SENT");
                email.setSentAt(Instant.now());
                emailRepository.save(email);
            } catch (Exception e) {
                System.err.println("Email send failed for " + recipient + ": " + e.getMessage());
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

    public void sendTermlyNewsletter(List<String> recipients, String subject, String body) {
        sendTargetedEmail(recipients, subject, body);
    }

    public Map<String, Object> forceSendUpcomingTermNewsletter() {
        TermWindow nextWindow = getNextTermWindow(LocalDate.now());
        LocalDate termStart = nextWindow.start();
        LocalDate termEnd = nextWindow.end();

        List<Announcement> upcoming = announcementRepository.findPublishedInDateRange(
                Arrays.asList("PUBLISHED", "APPROVED"),
                termStart,
                termEnd
        );

        List<String> recipients = userRepository.findByRole("Student").stream()
                .map(user -> user.getSchoolEmail())
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        if (!upcoming.isEmpty() && !recipients.isEmpty()) {
            String subject = "SOANAR Upcoming Events for Next Term (" + termStart + " to " + termEnd + ")";
            String body = buildUpcomingTermNewsletterHtml(upcoming, termStart, termEnd);
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
                "termStart", termStart.toString(),
                "termEnd", termEnd.toString(),
                "eventsIncluded", upcoming.size(),
                "recipients", recipients.size(),
                "sent", sent
        );
    }

    private TermWindow getNextTermWindow(LocalDate referenceDate) {
        int[] termStartMonths = {1, 4, 8}; // Jan, Apr, Aug
        int year = referenceDate.getYear();

        for (int month : termStartMonths) {
            LocalDate candidate = LocalDate.of(year, month, 1);
            if (candidate.isAfter(referenceDate)) {
                return buildTermWindow(candidate);
            }
        }

        return buildTermWindow(LocalDate.of(year + 1, termStartMonths[0], 1));
    }

    private TermWindow buildTermWindow(LocalDate start) {
        LocalDate nextStart;
        int month = start.getMonthValue();
        if (month == 1) {
            nextStart = LocalDate.of(start.getYear(), 4, 1);
        } else if (month == 4) {
            nextStart = LocalDate.of(start.getYear(), 8, 1);
        } else {
            nextStart = LocalDate.of(start.getYear() + 1, 1, 1);
        }

        LocalDate end = nextStart.minusDays(1);
        YearMonth endMonth = YearMonth.of(end.getYear(), end.getMonth());
        return new TermWindow(start, endMonth.atEndOfMonth());
    }

    private record TermWindow(LocalDate start, LocalDate end) {}

    private String buildUpcomingTermNewsletterHtml(List<Announcement> upcoming, LocalDate start, LocalDate end) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"margin:0;padding:0;background:#f3f2ef;font-family:Arial,Helvetica,sans-serif;\">");
        html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f2ef;padding:24px 0;\">");
        html.append("<tr><td align=\"center\">");
        html.append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;background:#ffffff;border:1px solid #d1d5db;border-radius:12px;overflow:hidden;\">");

        html.append("<tr><td style=\"padding:18px 24px;background:#0a66c2;color:#ffffff;font-size:15px;font-weight:700;\">SOANAR Updates</td></tr>");
        html.append("<tr><td style=\"padding:24px 24px 8px;color:#111827;font-size:34px;line-height:1.2;font-weight:700;\">Upcoming Events for Next Term</td></tr>");
        html.append("<tr><td style=\"padding:0 24px 18px;color:#4b5563;font-size:15px;\"><strong>Coverage:</strong> ")
            .append(start)
            .append(" to ")
            .append(end)
            .append("</td></tr>");

        for (Announcement announcement : upcoming) {
            html.append("<tr><td style=\"padding:0 24px 12px;\">");
            html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;\">");
            html.append("<tr><td style=\"padding:14px 16px 6px;color:#111827;font-size:18px;font-weight:700;\">")
                .append(escapeHtml(announcement.getTitle()))
                .append("</td></tr>");
            html.append("<tr><td style=\"padding:0 16px 8px;color:#374151;font-size:14px;\">Date: ")
                .append(announcement.getStartDate() != null ? announcement.getStartDate() : "TBD")
                .append(announcement.getEndDate() != null ? " to " + announcement.getEndDate() : "")
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
}
