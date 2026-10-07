package com.miniplm.repository;

import com.miniplm.model.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {

    boolean existsByPartNumber(String partNumber);

    Optional<Part> findByPartNumber(String partNumber);

    @Query("select p from Part p where lower(p.partNumber) like lower(concat('%', :q, '%')) " +
           "or lower(p.name) like lower(concat('%', :q, '%'))")
    List<Part> search(@Param("q") String q);
}
