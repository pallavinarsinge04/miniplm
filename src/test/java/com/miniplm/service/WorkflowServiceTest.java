package com.miniplm.service;

import com.miniplm.dto.DecisionRequest;
import com.miniplm.dto.SubmitRequest;
import com.miniplm.dto.TaskResponse;
import com.miniplm.exception.BusinessRuleException;
import com.miniplm.exception.ResourceNotFoundException;
import com.miniplm.model.ApprovalStatus;
import com.miniplm.model.ApprovalTask;
import com.miniplm.model.LifecycleState;
import com.miniplm.model.Part;
import com.miniplm.model.PartVersion;
import com.miniplm.model.Role;
import com.miniplm.model.User;
import com.miniplm.repository.ApprovalTaskRepository;
import com.miniplm.repository.PartRepository;
import com.miniplm.repository.PartVersionRepository;
import com.miniplm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static com.miniplm.TestData.part;
import static com.miniplm.TestData.user;
import static com.miniplm.TestData.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceTest {

    @Mock
    PartRepository partRepository;
    @Mock
    PartVersionRepository versionRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ApprovalTaskRepository taskRepository;
    @Mock
    AuditService auditService;
    @Mock
    CurrentUserService currentUserService;

    @InjectMocks
    WorkflowService service;

    private final Part part = part(1L, "P-100");
    private final User asha = user(5L, "asha@plm.com", Role.REVIEWER);
    private final User ravi = user(6L, "ravi@plm.com", Role.REVIEWER);
    private PartVersion revision;

    @BeforeEach
    void setUp() {
        revision = version(10L, part, "A", LifecycleState.IN_WORK);

        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(revision));
        when(userRepository.findByEmail("asha@plm.com")).thenReturn(Optional.of(asha));
        when(userRepository.findByEmail("ravi@plm.com")).thenReturn(Optional.of(ravi));
        when(taskRepository.save(any(ApprovalTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any(PartVersion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- submit ----------

    @Test
    void submitCreatesOneTaskPerReviewerAndStartsTheReview() {
        // The same reviewer written twice, with different capitals and spaces, counts once
        SubmitRequest request = new SubmitRequest(List.of(" Asha@PLM.com", "ravi@plm.com", "asha@plm.com"));

        List<TaskResponse> tasks = service.submit(1L, 10L, request);

        assertThat(tasks).hasSize(2);
        assertThat(tasks).allMatch(t -> t.status().equals("PENDING"));
        assertThat(revision.getState()).isEqualTo(LifecycleState.UNDER_REVIEW);
        verify(taskRepository, times(2)).save(any(ApprovalTask.class));
    }

    @Test
    void onlyAnInWorkRevisionCanBeSubmitted() {
        revision.setState(LifecycleState.RELEASED);

        assertThatThrownBy(() -> service.submit(1L, 10L, new SubmitRequest(List.of("asha@plm.com"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("IN_WORK");
        verify(taskRepository, never()).save(any(ApprovalTask.class));
    }

    @Test
    void aReviewerMustHaveTheReviewerRole() {
        User designer = user(7L, "dev@plm.com", Role.DESIGNER);
        when(userRepository.findByEmail("dev@plm.com")).thenReturn(Optional.of(designer));

        assertThatThrownBy(() -> service.submit(1L, 10L, new SubmitRequest(List.of("dev@plm.com"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a REVIEWER");
        assertThat(revision.getState()).isEqualTo(LifecycleState.IN_WORK);
    }

    @Test
    void anUnknownReviewerIsReported() {
        when(userRepository.findByEmail("ghost@plm.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(1L, 10L, new SubmitRequest(List.of("ghost@plm.com"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- decisions ----------

    private ApprovalTask taskFor(User reviewer, long id) {
        ApprovalTask task = new ApprovalTask();
        task.setId(id);
        task.setPartVersion(revision);
        task.setApprover(reviewer);
        return task;   // status starts as PENDING
    }

    private ApprovalTask underReviewWithTaskFor(User reviewer) {
        revision.setState(LifecycleState.UNDER_REVIEW);
        ApprovalTask task = taskFor(reviewer, 7L);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(currentUserService.email()).thenReturn(reviewer.getEmail());
        return task;
    }

    @Test
    void anApprovalWithOthersStillPendingKeepsTheRevisionUnderReview() {
        ApprovalTask task = underReviewWithTaskFor(asha);
        when(taskRepository.countByPartVersionIdAndStatus(10L, ApprovalStatus.PENDING)).thenReturn(1L);

        service.decide(7L, new DecisionRequest(ApprovalStatus.APPROVED, "Looks good"));

        assertThat(task.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(task.getDecidedAt()).isNotNull();
        assertThat(revision.getState()).isEqualTo(LifecycleState.UNDER_REVIEW);
        verify(versionRepository, never()).save(any(PartVersion.class));
    }

    @Test
    void theLastApprovalApprovesTheRevision() {
        underReviewWithTaskFor(asha);
        when(taskRepository.countByPartVersionIdAndStatus(10L, ApprovalStatus.PENDING)).thenReturn(0L);

        service.decide(7L, new DecisionRequest(ApprovalStatus.APPROVED, null));

        assertThat(revision.getState()).isEqualTo(LifecycleState.APPROVED);
        verify(versionRepository).save(revision);
    }

    @Test
    void aRejectionSendsTheRevisionBackToInWorkAndClosesTheOtherTasks() {
        underReviewWithTaskFor(asha);
        ApprovalTask ravisTask = taskFor(ravi, 8L);
        List<ApprovalTask> stillOpen = List.of(ravisTask);
        when(taskRepository.findByPartVersionIdAndStatus(10L, ApprovalStatus.PENDING)).thenReturn(stillOpen);

        service.decide(7L, new DecisionRequest(ApprovalStatus.REJECTED, "Wrong material"));

        assertThat(revision.getState()).isEqualTo(LifecycleState.IN_WORK);
        verify(taskRepository).deleteAll(stillOpen);
    }

    @Test
    void aRejectionNeedsAComment() {
        ApprovalTask task = underReviewWithTaskFor(asha);

        assertThatThrownBy(() -> service.decide(7L, new DecisionRequest(ApprovalStatus.REJECTED, "   ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("comment is required");
        assertThat(task.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(revision.getState()).isEqualTo(LifecycleState.UNDER_REVIEW);
    }

    @Test
    void aReviewerCannotDecideSomeoneElsesTask() {
        underReviewWithTaskFor(asha);
        when(currentUserService.email()).thenReturn("ravi@plm.com");   // logged in as Ravi

        assertThatThrownBy(() -> service.decide(7L, new DecisionRequest(ApprovalStatus.APPROVED, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("another reviewer");
    }

    @Test
    void aTaskCanOnlyBeDecidedOnce() {
        ApprovalTask task = underReviewWithTaskFor(asha);
        task.setStatus(ApprovalStatus.APPROVED);

        assertThatThrownBy(() -> service.decide(7L, new DecisionRequest(ApprovalStatus.REJECTED, "Changed my mind")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already decided");
    }

    @Test
    void noDecisionIsPossibleOnceTheReviewIsOver() {
        underReviewWithTaskFor(asha);
        revision.setState(LifecycleState.IN_WORK);

        assertThatThrownBy(() -> service.decide(7L, new DecisionRequest(ApprovalStatus.APPROVED, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not under review");
    }

    @Test
    void anUnknownTaskIsReported() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(99L, new DecisionRequest(ApprovalStatus.APPROVED, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
