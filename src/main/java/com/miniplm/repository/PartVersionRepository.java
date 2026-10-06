package com.miniplm.repository;

import com.miniplm.model.PartVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartVersionRepository extends JpaRepository<PartVersion, Long> {

    List<PartVersion> findByPartIdOrderByIdAsc(Long partId);

    Optional<PartVersion> findFirstByPartIdOrderByIdDesc(Long partId);
}
