package com.soanar.service;

import com.soanar.model.AuditLog;
import com.soanar.model.User;
import com.soanar.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog log(User user, String organizationId, String actionType, String entityType, Long entityId, 
                       String oldValue, String newValue, String description, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setOrganizationId(organizationId);
        log.setActionType(actionType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setDescription(description);
        log.setIpAddress(getClientIp(request));
        log.setTimestamp(Instant.now());
        return auditLogRepository.save(log);
    }

    @Transactional
    public AuditLog log(User user, String organizationId, String actionType, String entityType, Long entityId, String description) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setOrganizationId(organizationId);
        log.setActionType(actionType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setTimestamp(Instant.now());
        return auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogsByOrganization(String organizationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findByOrganizationId(organizationId, pageable);
    }

    public Page<AuditLog> getLogsByUser(User user, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findByUser(user, pageable);
    }

    public Page<AuditLog> getLogsByEntity(String entityType, Long entityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    public Page<AuditLog> getLogsByActionType(String actionType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findByActionType(actionType, pageable);
    }

    public List<AuditLog> getLogsInTimeRange(String organizationId, Instant start, Instant end) {
        return auditLogRepository.findByOrganizationIdAndTimestampBetween(organizationId, start, end);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
