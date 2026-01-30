package com.soanar.service;

import com.soanar.model.Email;
import com.soanar.repository.EmailRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ByteArrayResource;

import java.time.Instant;
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

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailRepository emailRepository;

    public EmailService(JavaMailSender mailSender, EmailRepository emailRepository) {
        this.mailSender = mailSender;
        this.emailRepository = emailRepository;
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
}
