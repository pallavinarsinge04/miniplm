package com.miniplm.controller;

import com.miniplm.dto.DecisionRequest;
import com.miniplm.dto.SubmitRequest;
import com.miniplm.dto.TaskResponse;
import com.miniplm.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /** Send a revision for review. */
    @PostMapping("/parts/{partId}/versions/{versionId}/submit")
    public List<TaskResponse> submit(@PathVariable Long partId, @PathVariable Long versionId,
                                     @Valid @RequestBody SubmitRequest request) {
        return workflowService.submit(partId, versionId, request);
    }

    /** Approval history of one revision. */
    @GetMapping("/parts/{partId}/versions/{versionId}/tasks")
    public List<TaskResponse> tasksForVersion(@PathVariable Long partId, @PathVariable Long versionId) {
        return workflowService.tasksForVersion(partId, versionId);
    }

    /** A reviewer's open tasks (the approval inbox). */
    @GetMapping("/tasks")
    public List<TaskResponse> inbox(@RequestParam String reviewerEmail) {
        return workflowService.inbox(reviewerEmail);
    }

    /** Approve or reject a task. */
    @PostMapping("/tasks/{taskId}/decision")
    public TaskResponse decide(@PathVariable Long taskId, @Valid @RequestBody DecisionRequest request) {
        return workflowService.decide(taskId, request);
    }
}
