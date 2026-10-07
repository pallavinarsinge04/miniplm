package com.miniplm.repository;

import com.miniplm.model.BomLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomLinkRepository extends JpaRepository<BomLink, Long> {

    boolean existsByParentVersionIdAndChildPartId(Long parentVersionId, Long childPartId);

    /** Where-used: every BOM line that uses this part as a child. */
    List<BomLink> findByChildPartId(Long childPartId);
}
