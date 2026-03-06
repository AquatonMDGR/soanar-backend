package com.soanar.config;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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
    private final boolean authEnforceDomain;
    private final String authAllowedDomain;

    public CustomOAuth2UserService(
            UserService userService,
            @Value("${auth.enforce-domain:true}") boolean authEnforceDomain,
            @Value("${auth.allowed-domain:iacademy.edu.ph}") String authAllowedDomain) {
        this.userService = userService;
        this.authEnforceDomain = authEnforceDomain;
        this.authAllowedDomain = authAllowedDomain;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate to default implementation
        org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService delegate = new org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService();
        OAuth2User oauthUser = delegate.loadUser(userRequest);

        Map<String, Object> attrs = oauthUser.getAttributes();
        String email = (String) attrs.getOrDefault("email", attrs.get("preferred_username"));
        String name = (String) attrs.getOrDefault("name", "");
        String picture = (String) attrs.getOrDefault("picture", "");

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found in OAuth2 response");
        }

        if (authEnforceDomain && !isAllowedEmailDomain(email)) {
            throw new OAuth2AuthenticationException("Only @" + authAllowedDomain + " emails are allowed");
        }

        // Basic role resolution: default to Student; admin roles assigned via Admin API
        String role = "Student";

        userService.createOrUpdate(email, role, name, picture);

        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role)), attrs, "email");
    }

    private boolean isAllowedEmailDomain(String email) {
        return email != null
                && authAllowedDomain != null
                && !authAllowedDomain.isBlank()
                && email.toLowerCase().endsWith("@" + authAllowedDomain.toLowerCase());
    }
}
