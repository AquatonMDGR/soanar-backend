package com.soanar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // disable CSRF for testing APIs
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // allow all requests without auth
            )
            .httpBasic().disable(); // disable basic auth popup

        return http.build();
    }
}
