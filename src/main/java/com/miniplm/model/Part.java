package com.miniplm.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Part master: one row per part number. Revisions live in PartVersion. */
@Entity
@Table(name = "part")
@Getter @Setter @NoArgsConstructor
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String partNumber;

    @Column(nullable = false)
    private String name;

    private String description;

    private String type; // e.g. ASSEMBLY, COMPONENT

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL)
    private List<PartVersion> versions = new ArrayList<>();
}
