package com.miniplm.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;      // e.g. SUBMITTED, APPROVED, REVISED
    private String entityType;  // e.g. PartVersion
    private Long entityId;
    private String performedBy;
    private LocalDateTime performedAt = LocalDateTime.now();
}
