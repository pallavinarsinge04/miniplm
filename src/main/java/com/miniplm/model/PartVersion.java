package com.miniplm.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A revision (A, B, C...) of a Part. Carries the lifecycle state. */
@Entity
@Table(name = "part_version",
       uniqueConstraints = @UniqueConstraint(columnNames = {"part_id", "revision"}))
@Getter @Setter @NoArgsConstructor
public class PartVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(nullable = false, length = 5)
    private String revision = "A";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LifecycleState state = LifecycleState.IN_WORK;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Children of this version in the BOM
    @OneToMany(mappedBy = "parentVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomLink> children = new ArrayList<>();
}
