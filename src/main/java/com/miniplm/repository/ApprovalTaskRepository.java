package com.miniplm.repository;

import com.miniplm.model.ApprovalStatus;
import com.miniplm.model.ApprovalTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    List<ApprovalTask> findByApproverEmailAndStatusOrderByIdAsc(String email, ApprovalStatus status);

    List<ApprovalTask> findByPartVersionIdOrderByIdAsc(Long partVersionId);

    List<ApprovalTask> findByPartVersionIdAndStatus(Long partVersionId, ApprovalStatus status);

    long countByPartVersionIdAndStatus(Long partVersionId, ApprovalStatus status);
}
