package com.soanar.service;

import com.soanar.model.AuditLog;
import com.soanar.model.User;
import com.soanar.repository.AuditLogRepository;
import com.soanar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminService(UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void updateUserRole(String email, String newRole) {
        User user = userRepository.findBySchoolEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (newRole == null || newRole.isBlank()) {
            throw new RuntimeException("Role is required");
        }

        String currentRole = user.getRole();

        // Governance rule:
        // 1) No one can be promoted into Super Admin from this endpoint.
        // 2) Existing Super Admin users cannot be demoted from this endpoint.
        if (!"Super Admin".equals(currentRole) && "Super Admin".equals(newRole)) {
            throw new RuntimeException("Promoting users to Super Admin is not allowed");
        }
        if ("Super Admin".equals(currentRole) && !"Super Admin".equals(newRole)) {
            throw new RuntimeException("Super Admin role cannot be changed");
        }

        user.setRole(newRole);
        userRepository.save(user);
    }

    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }
}
