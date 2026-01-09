package com.soanar.service;

import com.soanar.model.User;
import com.soanar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void updateUserRole(String email, String newRole) {
        User user = userRepository.findBySchoolEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(newRole);
        userRepository.save(user);
    }

    public List<String> getAuditLogs() {
        return List.of("Audit logs placeholder");
    }
}
