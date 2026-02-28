package com.soanar.repository;

import com.soanar.model.AuditLog;
import com.soanar.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByOrganizationId(String organizationId, Pageable pageable);
    Page<AuditLog> findByUser(User user, Pageable pageable);
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);
    Page<AuditLog> findByActionType(String actionType, Pageable pageable);
    Page<AuditLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);
    List<AuditLog> findByOrganizationIdAndTimestampBetween(String organizationId, Instant start, Instant end);
    List<AuditLog> findTop100ByOrderByTimestampDesc();
}
