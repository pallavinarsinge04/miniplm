package com.miniplm.service;

import com.miniplm.dto.DecisionRequest;
import com.miniplm.dto.SubmitRequest;
import com.miniplm.dto.TaskResponse;
import com.miniplm.exception.BusinessRuleException;
import com.miniplm.exception.ResourceNotFoundException;
import com.miniplm.model.ApprovalStatus;
import com.miniplm.model.ApprovalTask;
import com.miniplm.model.LifecycleState;
import com.miniplm.model.PartVersion;
import com.miniplm.model.Role;
import com.miniplm.model.User;
import com.miniplm.repository.ApprovalTaskRepository;
import com.miniplm.repository.PartRepository;
import com.miniplm.repository.PartVersionRepository;
import com.miniplm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Approval workflow:
 *   submit  : IN_WORK -> UNDER_REVIEW  (one PENDING task per reviewer)
 *   approve : when every reviewer approved -> APPROVED
 *   reject  : any reviewer rejects         -> back to IN_WORK
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final PartRepository partRepository;
    private final PartVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final ApprovalTaskRepository taskRepository;
    private final AuditService auditService;

    @Transactional
    public List<TaskResponse> submit(Long partId, Long versionId, SubmitRequest req) {
        PartVersion version = findVersion(partId, versionId);

        if (version.getState() != LifecycleState.IN_WORK) {
            throw new BusinessRuleException("Only an IN_WORK revision can be submitted. Revision "
                    + version.getRevision() + " is " + version.getState() + ".");
        }

        // Clean, de-duplicated reviewer list
        Set<String> emails = new LinkedHashSet<>();
        for (String e : req.reviewerEmails()) {
            emails.add(e.trim().toLowerCase());
        }

        List<User> reviewers = new ArrayList<>();
        for (String email : emails) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found: " + email));
            if (user.getRole() != Role.REVIEWER) {
                throw new BusinessRuleException(email + " is not a REVIEWER.");
            }
            reviewers.add(user);
        }

        List<TaskResponse> created = new ArrayList<>();
        for (User reviewer : reviewers) {
            ApprovalTask task = new ApprovalTask();
            task.setPartVersion(version);
            task.setApprover(reviewer);
            task.setStatus(ApprovalStatus.PENDING);
            created.add(toResponse(taskRepository.save(task)));
        }

        version.setState(LifecycleState.UNDER_REVIEW);
        versionRepository.save(version);

        auditService.log("REVISION_SUBMITTED", "PartVersion", versionId,
                "Part " + version.getPart().getPartNumber() + " rev " + version.getRevision()
                        + " sent to: " + String.join(", ", emails));
        return created;
    }

    @Transactional
    public TaskResponse decide(Long taskId, DecisionRequest req) {
        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        PartVersion version = task.getPartVersion();
        String reviewerEmail = req.reviewerEmail().trim().toLowerCase();

        if (!task.getApprover().getEmail().equalsIgnoreCase(reviewerEmail)) {
            throw new BusinessRuleException("This task is assigned to another reviewer.");
        }
        if (task.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessRuleException("This task was already decided: " + task.getStatus() + ".");
        }
        if (version.getState() != LifecycleState.UNDER_REVIEW) {
            throw new BusinessRuleException("Revision " + version.getRevision() + " is not under review.");
        }

        ApprovalStatus decision = req.decision();
        if (decision == ApprovalStatus.PENDING) {
            throw new BusinessRuleException("Decision must be APPROVED or REJECTED.");
        }
        String comment = req.comment() == null ? null : req.comment().trim();
        if (decision == ApprovalStatus.REJECTED && (comment == null || comment.isEmpty())) {
            throw new BusinessRuleException("A comment is required when rejecting.");
        }

        task.setStatus(decision);
        task.setComment(comment);
        task.setDecidedAt(LocalDateTime.now());
        taskRepository.save(task);

        String partNumber = version.getPart().getPartNumber();
        auditService.log("APPROVAL_" + decision, "PartVersion", version.getId(), reviewerEmail,
                partNumber + " rev " + version.getRevision()
                        + (comment == null || comment.isEmpty() ? "" : ": " + comment));

        if (decision == ApprovalStatus.REJECTED) {
            // One rejection ends the round: remove the other open tasks, send back for rework
            List<ApprovalTask> open = taskRepository
                    .findByPartVersionIdAndStatus(version.getId(), ApprovalStatus.PENDING);
            taskRepository.deleteAll(open);

            version.setState(LifecycleState.IN_WORK);
            versionRepository.save(version);
            auditService.log("REVISION_REJECTED", "PartVersion", version.getId(), reviewerEmail,
                    partNumber + " rev " + version.getRevision() + " returned to IN_WORK");
        } else if (taskRepository.countByPartVersionIdAndStatus(version.getId(), ApprovalStatus.PENDING) == 0) {
            version.setState(LifecycleState.APPROVED);
            versionRepository.save(version);
            auditService.log("REVISION_APPROVED", "PartVersion", version.getId(), reviewerEmail,
                    partNumber + " rev " + version.getRevision() + " approved by all reviewers");
        }
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> inbox(String reviewerEmail) {
        return taskRepository
                .findByApproverEmailAndStatusOrderByIdAsc(reviewerEmail.trim().toLowerCase(), ApprovalStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> tasksForVersion(Long partId, Long versionId) {
        findVersion(partId, versionId);
        return taskRepository.findByPartVersionIdOrderByIdAsc(versionId)
                .stream().map(this::toResponse).toList();
    }

    // ---------- helpers ----------

    private PartVersion findVersion(Long partId, Long versionId) {
        partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Part not found: " + partId));
        return versionRepository.findById(versionId)
                .filter(v -> v.getPart().getId().equals(partId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionId + " not found for part " + partId));
    }

    private TaskResponse toResponse(ApprovalTask t) {
        PartVersion v = t.getPartVersion();
        return new TaskResponse(t.getId(), v.getPart().getId(), v.getPart().getPartNumber(),
                v.getPart().getName(), v.getId(), v.getRevision(),
                t.getApprover().getEmail(), t.getStatus().name(), t.getComment(), t.getDecidedAt());
    }
}
