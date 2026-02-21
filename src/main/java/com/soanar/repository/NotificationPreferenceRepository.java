package com.soanar.repository;

import com.soanar.model.NotificationPreference;
import com.soanar.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUserAndOrganizationId(User user, String organizationId);
}
