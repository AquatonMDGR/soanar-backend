package com.soanar.repository;

import com.soanar.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientEmail(String recipientEmail);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readAt = :readTime WHERE n.recipientEmail = :email AND n.readAt IS NULL")
    void markAllAsReadForUser(@Param("email") String email, @Param("readTime") Instant readTime);
}
