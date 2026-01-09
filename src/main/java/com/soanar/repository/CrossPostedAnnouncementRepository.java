package com.soanar.repository;

import com.soanar.model.CrossPostedAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrossPostedAnnouncementRepository extends JpaRepository<CrossPostedAnnouncement, Long> {
}
