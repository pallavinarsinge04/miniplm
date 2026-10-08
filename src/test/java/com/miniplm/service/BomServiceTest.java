package com.miniplm.service;

import com.miniplm.dto.BomAddRequest;
import com.miniplm.dto.BomNode;
import com.miniplm.exception.BusinessRuleException;
import com.miniplm.exception.ResourceNotFoundException;
import com.miniplm.model.BomLink;
import com.miniplm.model.LifecycleState;
import com.miniplm.model.Part;
import com.miniplm.model.PartVersion;
import com.miniplm.repository.BomLinkRepository;
import com.miniplm.repository.PartRepository;
import com.miniplm.repository.PartVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BomServiceTest {

    @Mock
    PartRepository partRepository;
    @Mock
    PartVersionRepository versionRepository;
    @Mock
    BomLinkRepository bomLinkRepository;

    @InjectMocks
    BomService service;

    // Three parts used in most tests
    private final Part a = part(1L, "A-1");
    private final Part b = part(2L, "B-1");
    private final Part c = part(3L, "C-1");

    private PartVersion versionOfA;
    private PartVersion versionOfB;
    private PartVersion versionOfC;

    @BeforeEach
    void setUp() {
        versionOfA = version(10L, a, "A", LifecycleState.IN_WORK);
        versionOfB = version(20L, b, "A", LifecycleState.RELEASED);
        versionOfC = version(30L, c, "A", LifecycleState.RELEASED);

        when(partRepository.findById(1L)).thenReturn(Optional.of(a));
        when(partRepository.findByPartNumber("A-1")).thenReturn(Optional.of(a));
        when(partRepository.findByPartNumber("B-1")).thenReturn(Optional.of(b));
        when(partRepository.findByPartNumber("C-1")).thenReturn(Optional.of(c));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(1L)).thenReturn(Optional.of(versionOfA));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(2L)).thenReturn(Optional.of(versionOfB));
        when(versionRepository.findFirstByPartIdOrderByIdDesc(3L)).thenReturn(Optional.of(versionOfC));
        when(bomLinkRepository.save(any(BomLink.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aChildPartCanBeAdded() {
        BomNode node = service.addChild(1L, new BomAddRequest("B-1", 4, null));

        assertThat(node.partNumber()).isEqualTo("B-1");
        assertThat(node.quantity()).isEqualTo(4);
        assertThat(node.unit()).isEqualTo("EA");          // default unit
        assertThat(node.level()).isEqualTo(1);
        assertThat(versionOfA.getChildren()).hasSize(1);
    }

    @Test
    void aPartCannotContainItself() {
        assertThatThrownBy(() -> service.addChild(1L, new BomAddRequest("A-1", 1, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("itself");
        verify(bomLinkRepository, never()).save(any(BomLink.class));
    }

    @Test
    void theSameChildCannotBeAddedTwice() {
        when(bomLinkRepository.existsByParentVersionIdAndChildPartId(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.addChild(1L, new BomAddRequest("B-1", 1, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in this BOM");
        verify(bomLinkRepository, never()).save(any(BomLink.class));
    }

    @Test
    void aDirectLoopIsRejected() {
        // B already contains A, so adding B under A would make A -> B -> A
        versionOfB.getChildren().add(link(101L, versionOfB, a, 1));

        assertThatThrownBy(() -> service.addChild(1L, new BomAddRequest("B-1", 1, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Circular BOM");
        verify(bomLinkRepository, never()).save(any(BomLink.class));
    }

    @Test
    void aLoopThroughSeveralLevelsIsRejected() {
        // C contains B, and B contains A. Adding C under A would make A -> C -> B -> A
        versionOfC.getChildren().add(link(100L, versionOfC, b, 1));
        versionOfB.getChildren().add(link(101L, versionOfB, a, 1));

        assertThatThrownBy(() -> service.addChild(1L, new BomAddRequest("C-1", 1, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Circular BOM");
        verify(bomLinkRepository, never()).save(any(BomLink.class));
    }

    @Test
    void aBranchThatDoesNotLeadBackIsAllowed() {
        // C contains B, but B does not contain A: no loop
        versionOfC.getChildren().add(link(100L, versionOfC, b, 1));

        BomNode node = service.addChild(1L, new BomAddRequest("C-1", 2, "EA"));

        assertThat(node.partNumber()).isEqualTo("C-1");
        assertThat(versionOfA.getChildren()).hasSize(1);
    }

    @Test
    void aReleasedBomCannotBeChanged() {
        versionOfA.setState(LifecycleState.RELEASED);

        assertThatThrownBy(() -> service.addChild(1L, new BomAddRequest("B-1", 1, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be changed");
        verify(bomLinkRepository, never()).save(any(BomLink.class));
    }

    @Test
    void anUnknownChildPartIsReported() {
        when(partRepository.findByPartNumber("X-9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addChild(1L, new BomAddRequest("X-9", 1, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theTreeShowsEveryLevel() {
        // A contains B, and B contains C
        versionOfA.getChildren().add(link(100L, versionOfA, b, 2));
        versionOfB.getChildren().add(link(101L, versionOfB, c, 5));

        BomNode root = service.getTree(1L);

        assertThat(root.level()).isEqualTo(0);
        assertThat(root.children()).hasSize(1);
        BomNode level1 = root.children().get(0);
        assertThat(level1.partNumber()).isEqualTo("B-1");
        assertThat(level1.quantity()).isEqualTo(2);
        assertThat(level1.children()).hasSize(1);
        assertThat(level1.children().get(0).partNumber()).isEqualTo("C-1");
        assertThat(level1.children().get(0).level()).isEqualTo(2);
    }
}
