package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.repository.AnnouncementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RecipientResolverService recipientResolverService;

    @InjectMocks
    private AnnouncementService announcementService;

    @Test
    void createShortensLongUrlInDescription() {
        User poster = new User();
        poster.setRole("OSAS");
        poster.setName("Poster");

        Announcement announcement = new Announcement();
        announcement.setTitle("Event");
        announcement.setDescription("Join our event here: https://example.com/events/2026/spring/community-meetup-registration-page");

        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        announcementService.create(announcement, poster);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());

        String savedDescription = captor.getValue().getDescription();
        assertTrue(savedDescription.startsWith("Join our event here: "));
        assertTrue(savedDescription.contains("https://"));
        assertTrue(savedDescription.contains("..."));
        assertTrue(savedDescription.endsWith("page"));
        assertFalse(savedDescription.contains("community-meetup-registration-page"));
    }

    @Test
    void createShortensMultipleUrlsButKeepsShortUrlsAndText() {
        User poster = new User();
        poster.setRole("Student Organization");
        poster.setName("Poster");

        Announcement announcement = new Announcement();
        announcement.setTitle("Event");
        announcement.setDescription(
                "Short stays https://t.co/xYz1 and long one https://example.com/path/to/really/long/resource/with/details/edit plus plain text."
        );

        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        announcementService.create(announcement, poster);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());

        String savedDescription = captor.getValue().getDescription();
        assertTrue(savedDescription.contains("https://t.co/xYz1"));
        assertTrue(savedDescription.contains("plus plain text."));
        assertTrue(savedDescription.contains("...edit"));
        assertFalse(savedDescription.contains("really/long/resource/with/details/edit"));
    }
}
