package com.miniplm.service;

import com.miniplm.dto.PartRequest;
import com.miniplm.dto.PartResponse;
import com.miniplm.dto.VersionResponse;
import com.miniplm.exception.DuplicateResourceException;
import com.miniplm.exception.ResourceNotFoundException;
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
        // Part number is the identity, so it is not changed here
        part.setName(req.name());
        part.setDescription(req.description());
        part.setType(req.type());
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public void delete(Long id) {
        partRepository.delete(find(id));
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(Long id) {
        find(id);
        return versionRepository.findByPartIdOrderByIdAsc(id).stream()
                .map(v -> new VersionResponse(v.getId(), v.getRevision(),
                        v.getState().name(), v.getCreatedAt()))
                .toList();
    }

    private Part find(Long id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Part not found: " + id));
    }

    private PartResponse toResponse(Part p) {
        PartVersion latest = versionRepository.findFirstByPartIdOrderByIdDesc(p.getId()).orElse(null);
        return new PartResponse(
                p.getId(), p.getPartNumber(), p.getName(), p.getDescription(), p.getType(),
                latest == null ? null : latest.getRevision(),
                latest == null ? null : latest.getState().name());
    }
}
