package com.miniplm.service;

import com.miniplm.dto.PartRequest;
import com.miniplm.dto.PartResponse;
import com.miniplm.dto.TransitionRequest;
import com.miniplm.dto.VersionResponse;
import com.miniplm.exception.BusinessRuleException;
import com.miniplm.exception.DuplicateResourceException;
import com.miniplm.model.BomLink;
import com.miniplm.model.LifecycleState;
import com.miniplm.model.Part;
import com.miniplm.model.PartVersion;
import com.miniplm.repository.PartRepository;
import com.miniplm.repository.PartVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static com.miniplm.TestData.link;
import static com.miniplm.TestData.part;
import static com.miniplm.TestData.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartServiceTest {

    @Mock
    PartRepository partRepository;
    @Mock
    PartVersionRepository versionRepository;
    @Mock
    AuditService auditService;
    @Mock
    CurrentUserService currentUserService;

    @InjectMocks
    PartService service;

    private final PartRequest request = new PartRequest("P-100", "Bracket", "Mounting bracket", "COMPONENT");

    @BeforeEach
    void saveReturnsWhatItIsGiven() {
        when(partRepository.save(any(Part.class))).thenAnswer(inv -> {
            Part saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });
        when(versionRepository.save(any(PartVersion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- revision letters ----------

    @Test
    void revisionLettersFollowTheAlphabet() {
        assertThat(PartService.nextRevision("A")).isEqualTo("B");
        assertThat(PartService.nextRevision("B")).isEqualTo("C");
        assertThat(PartService.nextRevision("Z")).isEqualTo("AA");
        assertThat(PartService.nextRevision("AZ")).isEqualTo("BA");
        assertThat(PartService.nextRevision("ZZ")).isEqualTo("AAA");
    }

    // ---------- create ----------

    @Test
    void aNewPartStartsAtRevisionAInWork() {
        PartResponse response = service.create(request);

        ArgumentCaptor<PartVersion> captor = ArgumentCaptor.forClass(PartVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getRevision()).isEqualTo("A");
        assertThat(captor.getValue().getState()).isEqualTo(LifecycleState.IN_WORK);
        assertThat(response.partNumber()).isEqualTo("P-100");
        verify(auditService).log(eq("PART_CREATED"), eq("Part"), eq(1L), anyString());
    }

    @Test
    void aDuplicatePartNumberIsRejected() {
        when(partRepository.existsByPartNumber("P-100")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(partRepository, never()).save(any(Part.class));
    }

    // ---------- update / delete rules ----------

    @Test
    void aPartCanBeEditedWhileInWork() {
        Part part = part(1L, "P-100");
        PartVersion inWork = version(10L, part, "A", LifecycleState.IN_WORK);
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(1L)).thenReturn(Optional.of(inWork));

        service.update(1L, new PartRequest("P-100", "New name", "New description", "ASSEMBLY"));

        assertThat(part.getName()).isEqualTo("New name");
        assertThat(part.getType()).isEqualTo("ASSEMBLY");
    }

    @Test
    void aReleasedPartCannotBeEdited() {
        Part part = part(1L, "P-100");
        PartVersion released = version(10L, part, "A", LifecycleState.RELEASED);
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(1L)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be edited");
    }

    @Test
    void aPartThatWasEverReleasedCannotBeDeleted() {
        Part part = part(1L, "P-100");
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.existsByPartIdAndState(1L, LifecycleState.RELEASED)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("released");
        verify(partRepository, never()).delete(any(Part.class));
    }

    // ---------- release (the only manual transition) ----------

    @Test
    void anApprovedRevisionCanBeReleased() {
        Part part = part(1L, "P-100");
        PartVersion approved = version(10L, part, "A", LifecycleState.APPROVED);
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(approved));

        VersionResponse response = service.transition(1L, 10L, new TransitionRequest(LifecycleState.RELEASED));

        assertThat(response.state()).isEqualTo("RELEASED");
        assertThat(approved.getState()).isEqualTo(LifecycleState.RELEASED);
        verify(auditService).log(eq("REVISION_RELEASED"), eq("PartVersion"), eq(10L), anyString());
    }

    @Test
    void aRevisionThatIsNotApprovedCannotBeReleased() {
        Part part = part(1L, "P-100");
        PartVersion inWork = version(10L, part, "A", LifecycleState.IN_WORK);
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(inWork));

        assertThatThrownBy(() -> service.transition(1L, 10L, new TransitionRequest(LifecycleState.RELEASED)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot move");
        assertThat(inWork.getState()).isEqualTo(LifecycleState.IN_WORK);
    }

    @Test
    void reviewStatesCannotBeSetByHandAnyMore() {
        Part part = part(1L, "P-100");
        PartVersion inWork = version(10L, part, "A", LifecycleState.IN_WORK);
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findById(10L)).thenReturn(Optional.of(inWork));

        assertThatThrownBy(() -> service.transition(1L, 10L, new TransitionRequest(LifecycleState.UNDER_REVIEW)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("/submit");
        assertThat(inWork.getState()).isEqualTo(LifecycleState.IN_WORK);
    }

    // ---------- revise ----------

    @Test
    void reviseCreatesTheNextRevisionAndCopiesTheBom() {
        Part assembly = part(1L, "P-100");
        Part bolt = part(2L, "P-200");
        PartVersion released = version(10L, assembly, "A", LifecycleState.RELEASED);
        released.getChildren().add(link(100L, released, bolt, 3));
        when(partRepository.findById(1L)).thenReturn(Optional.of(assembly));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(1L)).thenReturn(Optional.of(released));

        VersionResponse response = service.revise(1L);

        assertThat(response.revision()).isEqualTo("B");
        assertThat(response.state()).isEqualTo("IN_WORK");

        ArgumentCaptor<PartVersion> captor = ArgumentCaptor.forClass(PartVersion.class);
        verify(versionRepository).save(captor.capture());
        PartVersion next = captor.getValue();
        assertThat(next.getChildren()).hasSize(1);
        BomLink copy = next.getChildren().get(0);
        assertThat(copy.getChildPart().getId()).isEqualTo(2L);
        assertThat(copy.getQuantity()).isEqualTo(3);
        assertThat(copy.getParentVersion()).isSameAs(next);
        verify(auditService).log(eq("REVISION_CREATED"), eq("PartVersion"), any(), anyString());
    }

    @Test
    void onlyAReleasedRevisionCanBeRevised() {
        Part part = part(1L, "P-100");
        PartVersion inWork = version(10L, part, "A", LifecycleState.IN_WORK);
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(1L)).thenReturn(Optional.of(inWork));

        assertThatThrownBy(() -> service.revise(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only a released revision");
        verify(versionRepository, never()).save(any(PartVersion.class));
    }
}
