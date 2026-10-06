package com.miniplm.service;

import com.miniplm.dto.PartRequest;
import com.miniplm.dto.PartResponse;
import com.miniplm.dto.TransitionRequest;
import com.miniplm.dto.VersionResponse;
import com.miniplm.exception.BusinessRuleException;
import com.miniplm.exception.DuplicateResourceException;
import com.miniplm.exception.ResourceNotFoundException;
import com.miniplm.model.BomLink;
import com.miniplm.model.LifecycleState;
import com.miniplm.model.Part;
import com.miniplm.model.PartVersion;
import com.miniplm.repository.PartRepository;
import com.miniplm.repository.PartVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PartService {

    private final PartRepository partRepository;
    private final PartVersionRepository versionRepository;

    @Transactional
    public PartResponse create(PartRequest req) {
        if (partRepository.existsByPartNumber(req.partNumber())) {
            throw new DuplicateResourceException("Part number already exists: " + req.partNumber());
        }
        Part part = new Part();
        part.setPartNumber(req.partNumber());
        part.setName(req.name());
        part.setDescription(req.description());
        part.setType(req.type());
        Part saved = partRepository.save(part);

        // Every new part starts at revision A, state IN_WORK
        PartVersion first = new PartVersion();
        first.setPart(saved);
        versionRepository.save(first);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PartResponse> search(String q) {
        List<Part> parts = (q == null || q.isBlank())
                ? partRepository.findAll()
                : partRepository.search(q.trim());
        return parts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PartResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PartResponse update(Long id, PartRequest req) {
        Part part = find(id);

        // Rule: only an In Work revision can be edited
        PartVersion latest = latestVersion(id);
        if (!latest.getState().isEditable()) {
            throw new BusinessRuleException("Revision " + latest.getRevision() + " is "
                    + latest.getState() + " and cannot be edited. Revise the part to make changes.");
        }

        // Part number is the identity, so it is not changed here
        part.setName(req.name());
        part.setDescription(req.description());
        part.setType(req.type());
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public void delete(Long id) {
        Part part = find(id);

        // Rule: a part that was ever released cannot be deleted
        if (versionRepository.existsByPartIdAndState(id, LifecycleState.RELEASED)) {
            throw new BusinessRuleException("A part with a released revision cannot be deleted.");
        }
        partRepository.delete(part);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(Long id) {
        find(id);
        return versionRepository.findByPartIdOrderByIdAsc(id).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    /** Move a revision through its lifecycle: IN_WORK -> UNDER_REVIEW -> APPROVED -> RELEASED. */
    @Transactional
    public VersionResponse transition(Long partId, Long versionId, TransitionRequest req) {
        find(partId);
        PartVersion version = versionRepository.findById(versionId)
                .filter(v -> v.getPart().getId().equals(partId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionId + " not found for part " + partId));

        LifecycleState current = version.getState();
        LifecycleState target = req.targetState();

        if (!current.canMoveTo(target)) {
            throw new BusinessRuleException("Cannot move revision " + version.getRevision()
                    + " from " + current + " to " + target + ".");
        }
        version.setState(target);
        return toVersionResponse(versionRepository.save(version));
    }

    /** Create the next revision (A -> B) from the latest RELEASED revision. */
    @Transactional
    public VersionResponse revise(Long partId) {
        Part part = find(partId);
        PartVersion latest = latestVersion(partId);

        if (latest.getState() != LifecycleState.RELEASED) {
            throw new BusinessRuleException("Only a released revision can be revised. Revision "
                    + latest.getRevision() + " is " + latest.getState() + ".");
        }

        PartVersion next = new PartVersion();
        next.setPart(part);
        next.setRevision(nextRevision(latest.getRevision()));
        // state defaults to IN_WORK

        // Like Windchill, the new revision starts with the same BOM
        for (BomLink old : latest.getChildren()) {
            BomLink copy = new BomLink();
            copy.setParentVersion(next);
            copy.setChildPart(old.getChildPart());
            copy.setQuantity(old.getQuantity());
            copy.setUnit(old.getUnit());
            next.getChildren().add(copy);
        }
        return toVersionResponse(versionRepository.save(next));
    }

    // ---------- helpers ----------

    private Part find(Long id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Part not found: " + id));
    }

    private PartVersion latestVersion(Long partId) {
        return versionRepository.findFirstByPartIdOrderByIdDesc(partId)
                .orElseThrow(() -> new ResourceNotFoundException("No revisions found for part " + partId));
    }

    /** A -> B ... Z -> AA -> AB ... */
    static String nextRevision(String rev) {
        char[] c = rev.toCharArray();
        for (int i = c.length - 1; i >= 0; i--) {
            if (c[i] == 'Z') {
                c[i] = 'A';
            } else {
                c[i]++;
                return new String(c);
            }
        }
        return "A" + new String(c);
    }

    private VersionResponse toVersionResponse(PartVersion v) {
        return new VersionResponse(v.getId(), v.getRevision(), v.getState().name(), v.getCreatedAt());
    }

    private PartResponse toResponse(Part p) {
        PartVersion latest = versionRepository.findFirstByPartIdOrderByIdDesc(p.getId()).orElse(null);
        return new PartResponse(
                p.getId(), p.getPartNumber(), p.getName(), p.getDescription(), p.getType(),
                latest == null ? null : latest.getRevision(),
                latest == null ? null : latest.getState().name());
    }
}
