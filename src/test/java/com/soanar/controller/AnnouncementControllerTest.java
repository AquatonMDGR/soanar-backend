package com.soanar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soanar.model.User;
import com.soanar.service.AnnouncementService;
import com.soanar.service.CrosspostService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnnouncementControllerTest {

    @Mock
    private AnnouncementService announcementService;

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CrosspostService crosspostService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnouncementController controller = new AnnouncementController(
                announcementService,
                userService,
                jwtUtil,
                crosspostService,
                new ObjectMapper()
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void pendingAnnouncementsAllowedWhenCurrentDbRoleIsOsasEvenIfTokenRoleIsOld() throws Exception {
        String token = "sample-token";
        String email = "tester@example.com";

        User user = new User();
        user.setSchoolEmail(email);
        user.setRole("OSAS");

        when(jwtUtil.extractEmail(token)).thenReturn(email);
        when(userService.findByEmail(email)).thenReturn(Optional.of(user));
        when(announcementService.getPending()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/announcements")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void pendingAnnouncementsForbiddenWhenCurrentDbRoleIsNotPrivileged() throws Exception {
        String token = "sample-token";
        String email = "studentorg@example.com";

        User user = new User();
        user.setSchoolEmail(email);
        user.setRole("Student Organization");

        when(jwtUtil.extractEmail(token)).thenReturn(email);
        when(userService.findByEmail(email)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/announcements")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Not authorized to view pending announcements\"}"));
    }
}
