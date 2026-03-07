package com.soanar.repository;

import com.soanar.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    @Query("SELECT a FROM Announcement a WHERE COALESCE(a.isDeleted, false) = false AND a.status = :status ORDER BY a.createdAt DESC")
    List<Announcement> findByStatus(@Param("status") String status);
    
    @Query("SELECT a FROM Announcement a WHERE COALESCE(a.isDeleted, false) = false AND a.status IN :statuses ORDER BY a.createdAt DESC")
    List<Announcement> findByStatusIn(@Param("statuses") List<String> statuses);

    @Query("SELECT a FROM Announcement a WHERE COALESCE(a.isDeleted, false) = false AND a.status IN :statuses AND a.startDate IS NOT NULL AND a.startDate BETWEEN :startDate AND :endDate ORDER BY a.startDate ASC")
    List<Announcement> findPublishedInDateRange(@Param("statuses") List<String> statuses,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    @Query("SELECT a FROM Announcement a WHERE COALESCE(a.isDeleted, false) = false ORDER BY a.createdAt DESC")
    List<Announcement> findAllActiveOrderByCreatedAtDesc();

    @Query("SELECT a FROM Announcement a WHERE a.id = :id AND COALESCE(a.isDeleted, false) = false")
    java.util.Optional<Announcement> findActiveById(@Param("id") Long id);
}
