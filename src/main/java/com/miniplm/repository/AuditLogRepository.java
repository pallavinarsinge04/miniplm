package com.miniplm.repository;

import com.miniplm.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);

    List<AuditLog> findTop100ByOrderByIdDesc();
}
