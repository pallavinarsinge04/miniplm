package com.miniplm.model;

import jakarta.persistence.*;
import lombok.*;

/** One BOM line: parent version uses a child part in a given quantity. */
@Entity
@Table(name = "bom_link")
@Getter @Setter @NoArgsConstructor
public class BomLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_version_id")
    private PartVersion parentVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_part_id")
    private Part childPart;

    @Column(nullable = false)
    private Integer quantity = 1;

    private String unit = "EA";
}
