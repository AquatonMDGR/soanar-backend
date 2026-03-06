package com.soanar.repository;

import com.soanar.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySchoolEmail(String schoolEmail);
    List<User> findByRole(String role);
    List<User> findByRoleAndIsActiveTrue(String role);
    List<User> findByRoleAndYearLevelIn(String role, List<String> yearLevels);
    List<User> findByRoleAndIsActiveTrueAndYearLevelIn(String role, List<String> yearLevels);
    List<User> findByRoleAndSchoolIn(String role, List<String> schools);
    List<User> findByRoleAndIsActiveTrueAndSchoolIn(String role, List<String> schools);
    List<User> findByRoleAndYearLevelInAndSchoolIn(String role, List<String> yearLevels, List<String> schools);
    List<User> findByRoleAndIsActiveTrueAndYearLevelInAndSchoolIn(String role, List<String> yearLevels, List<String> schools);
}
