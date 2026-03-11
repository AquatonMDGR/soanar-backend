package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.Notification;
import com.soanar.repository.DistributionGroupMemberRepository;
import com.soanar.repository.NotificationPreferenceRepository;
import com.soanar.repository.NotificationRepository;
import com.soanar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceLengthSafetyTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DistributionGroupMemberRepository distributionGroupMemberRepository;

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private RecipientResolverService recipientResolverService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createNotificationTruncatesLegacyColumnsButKeepsFullBody() {
        Announcement announcement = new Announcement();
        announcement.setId(347L);

        String longTitle = "T".repeat(320);
        String longMessage = "M".repeat(700);

        when(userRepository.findBySchoolEmail("student@iacademy.edu.ph")).thenReturn(Optional.empty());

        notificationService.createNotification(
                announcement,
                "student@iacademy.edu.ph",
                "announcement",
                longTitle,
                longMessage
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(255, saved.getTitle().length());
        assertEquals(255, saved.getMessage().length());
        assertEquals(700, saved.getBody().length());
        assertTrue(saved.getType().length() <= 50);
        assertTrue(saved.getRecipientEmail().length() <= 255);
    }

    @Test
    void notifyStudentsUsesDescriptionSnippetForNotificationBody() {
        Announcement announcement = new Announcement();
        announcement.setId(500L);
        announcement.setTitle("Very Long Announcement");
        announcement.setDescription("word ".repeat(120));

        when(recipientResolverService.resolveWithFallback(announcement))
                .thenReturn(Set.of("student@iacademy.edu.ph"));
        when(userRepository.findBySchoolEmail("student@iacademy.edu.ph"))
                .thenReturn(Optional.empty());

        notificationService.notifyStudentsOfPublishedAnnouncement(announcement);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertNotNull(saved.getBody());
        assertTrue(saved.getBody().length() <= 183);
        assertTrue(saved.getBody().endsWith("..."));
        assertFalse(saved.getBody().equals(announcement.getDescription()));
    }
}
