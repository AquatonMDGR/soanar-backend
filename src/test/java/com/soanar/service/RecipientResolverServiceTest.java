package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.repository.DistributionGroupMemberRepository;
import com.soanar.repository.UserRepository;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientResolverServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DistributionGroupMemberRepository distributionGroupMemberRepository;

    @InjectMocks
    private RecipientResolverService recipientResolverService;

    @Test
    void resolveWithFallbackHandlesLazyDistributionGroupsForOsasAnnouncement() {
        Announcement announcement = mock(Announcement.class);
        User poster = new User();
        poster.setRole("OSAS");

        User student = new User();
        student.setSchoolEmail("student1@iacademy.edu.ph");

        when(announcement.getPostedBy()).thenReturn(poster);
        when(announcement.getTargetYearLevels()).thenReturn(List.of());
        when(announcement.getTargetSchools()).thenReturn(List.of());
        when(announcement.getTargetManualEmails()).thenReturn(List.of());
        when(announcement.getDistributionGroups())
                .thenThrow(new LazyInitializationException("no Session"));

        when(userRepository.findByRoleAndIsActiveTrue("Student")).thenReturn(List.of(student));

        Set<String> recipients = recipientResolverService.resolveWithFallback(announcement);

        assertEquals(1, recipients.size());
        assertTrue(recipients.contains("student1@iacademy.edu.ph"));
    }
}
