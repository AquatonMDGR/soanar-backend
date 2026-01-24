package com.soanar.repository;

import com.soanar.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByStatus(String status);
    
    @Query("SELECT a FROM Announcement a WHERE a.status IN :statuses")
    List<Announcement> findByStatusIn(@Param("statuses") List<String> statuses);
}
