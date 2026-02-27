package com.soanar.repository;

import com.soanar.model.Notification;
import com.soanar.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
    // Phase 6 enhancements
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.user = :user OR n.recipientEmail = :email ORDER BY n.createdAt DESC")
    Page<Notification> findByUserOrRecipientEmailOrderByCreatedAtDesc(@Param("user") User user, @Param("email") String email, Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE n.user = ?1 AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUser(User user);

    @Query("SELECT n FROM Notification n WHERE (n.user = :user OR n.recipientEmail = :email) AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserOrEmail(@Param("user") User user, @Param("email") String email);
    
    @Query("SELECT n FROM Notification n WHERE n.recipientEmail = ?1 AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByEmail(String email);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = ?1 AND n.readAt IS NULL")
    long countUnreadByUser(User user);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.user = :user OR n.recipientEmail = :email) AND n.readAt IS NULL")
    long countUnreadByUserOrEmail(@Param("user") User user, @Param("email") String email);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientEmail = ?1 AND n.readAt IS NULL")
    long countUnreadByEmail(String email);
}
