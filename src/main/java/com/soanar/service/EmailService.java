package com.soanar.service;

import com.soanar.model.Email;
import com.soanar.repository.EmailRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);

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
