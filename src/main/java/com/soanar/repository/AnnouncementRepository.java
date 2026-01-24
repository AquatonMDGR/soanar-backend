package com.soanar.repository;

import com.soanar.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByStatus(String status);
    List<Announcement> findByStatusIn(List<String> statuses);
}
