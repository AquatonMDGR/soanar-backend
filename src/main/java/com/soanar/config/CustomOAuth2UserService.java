package com.soanar.config;

import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.soanar.service.UserService;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;

    public CustomOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate to default implementation
        org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService delegate = new org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService();
        OAuth2User oauthUser = delegate.loadUser(userRequest);

        Map<String, Object> attrs = oauthUser.getAttributes();
        String email = (String) attrs.getOrDefault("email", attrs.get("preferred_username"));
        String name = (String) attrs.getOrDefault("name", "");

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found in OAuth2 response");
        }

        // Email domain validation - only @iacademy.edu.ph emails allowed
        // TEMPORARILY DISABLED FOR TESTING MULTIPLE ROLES
        // if (!email.toLowerCase().endsWith("@iacademy.edu.ph")) {
        //     throw new OAuth2AuthenticationException("Only @iacademy.edu.ph emails are allowed");
        // }

        // Basic role resolution: default to Student; admin roles assigned via Admin API
        String role = "Student";

        userService.createOrUpdate(email, role, name);

        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role)), attrs, "email");
    }
}
