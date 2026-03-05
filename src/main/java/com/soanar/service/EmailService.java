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

    public EmailService(JavaMailSender mailSender,
                        EmailRepository emailRepository,
                        UserRepository userRepository,
                        AnnouncementRepository announcementRepository) {
        this.mailSender = mailSender;
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
    }

    @Transactional
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

    @Transactional
    public void sendTermlyNewsletter(List<String> recipients, String subject, String body) {
        sendTargetedEmail(recipients, subject, body);
    }

    @Transactional
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
            String subject = "SONAR Upcoming Events for Next Term (" + termStart + " to " + termEnd + ")";
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
        html.append("<html><body>");
        html.append("<h2>Upcoming Events for Next Term</h2>");
        html.append("<p><strong>Coverage:</strong> ").append(start).append(" to ").append(end).append("</p>");
        html.append("<ul>");

        for (Announcement announcement : upcoming) {
            html.append("<li style=\"margin-bottom:12px;\">")
                .append("<strong>").append(announcement.getTitle()).append("</strong><br/>")
                .append("Date: ").append(announcement.getStartDate())
                .append(announcement.getEndDate() != null ? " to " + announcement.getEndDate() : "")
                .append("<br/>")
                .append(announcement.getDescription() != null ? announcement.getDescription() : "")
                .append("</li>");
        }

        html.append("</ul>");
        html.append("<p>Best regards,<br/>SONAR</p>");
        html.append("</body></html>");
        return html.toString();
    }
}
