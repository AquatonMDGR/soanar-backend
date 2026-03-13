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

    private static final String DEFAULT_STUDENT_YEAR_LEVEL = "1st";
    private static final String DEFAULT_STUDENT_SCHOOL = "SODA";
    private static final List<String> ALLOWED_ROLES = List.of("Student", "Student Organization", "OSAS", "Academic", "Super Admin");
    private static final List<String> ALLOWED_YEAR_LEVELS = List.of("1st", "2nd", "3rd", "4th");
    private static final List<String> ALLOWED_SCHOOLS = List.of("SOC", "SODA", "SBLA");

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
    public User updateUserRole(String email, String newRole) {
        User user = userRepository.findBySchoolEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (newRole == null || newRole.isBlank()) {
            throw new RuntimeException("Role is required");
        }

        String normalizedRole = newRole.trim();
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new RuntimeException("Invalid role");
        }

        String currentRole = user.getRole();

        // Governance rule:
        // 1) No one can be promoted into Super Admin from this endpoint.
        // 2) Existing Super Admin users cannot be demoted from this endpoint.
        if (!"Super Admin".equals(currentRole) && "Super Admin".equals(normalizedRole)) {
            throw new RuntimeException("Promoting users to Super Admin is not allowed");
        }
        if ("Super Admin".equals(currentRole) && !"Super Admin".equals(normalizedRole)) {
            throw new RuntimeException("Super Admin role cannot be changed");
        }

        user.setRole(normalizedRole);
        if ("Student".equals(normalizedRole)) {
            user.setYearLevel(DEFAULT_STUDENT_YEAR_LEVEL);
            user.setSchool(DEFAULT_STUDENT_SCHOOL);
        } else {
            user.setYearLevel(null);
            user.setSchool(null);
        }
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserActiveStatus(String email, boolean isActive) {
        User user = userRepository.findBySchoolEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("Super Admin".equals(user.getRole()) && !isActive) {
            throw new RuntimeException("Super Admin account cannot be deactivated");
        }

        user.setIsActive(isActive);
        return userRepository.save(user);
    }

    @Transactional
    public User updateStudentProfileFields(String email, String yearLevel, String school) {
        User user = userRepository.findBySchoolEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"Student".equals(user.getRole())) {
            throw new RuntimeException("Only students can have year level and school updated");
        }

        if (yearLevel == null || yearLevel.isBlank()) {
            throw new RuntimeException("Year level is required");
        }
        if (school == null || school.isBlank()) {
            throw new RuntimeException("School is required");
        }

        String normalizedYearLevel = yearLevel.trim();
        String normalizedSchool = school.trim();

        if (!ALLOWED_YEAR_LEVELS.contains(normalizedYearLevel)) {
            throw new RuntimeException("Invalid year level");
        }
        if (!ALLOWED_SCHOOLS.contains(normalizedSchool)) {
            throw new RuntimeException("Invalid school");
        }

        user.setYearLevel(normalizedYearLevel);
        user.setSchool(normalizedSchool);
        return userRepository.save(user);
    }

    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }
}
