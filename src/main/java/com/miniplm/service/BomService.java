package com.miniplm.service;

import com.miniplm.dto.BomAddRequest;
import com.miniplm.dto.BomNode;
import com.miniplm.dto.BomUpdateRequest;
import com.miniplm.dto.WhereUsedResponse;
import com.miniplm.exception.BusinessRuleException;
import com.miniplm.exception.ResourceNotFoundException;
import com.miniplm.model.BomLink;
import com.miniplm.model.Part;
import com.miniplm.model.PartVersion;
import com.miniplm.repository.BomLinkRepository;
import com.miniplm.repository.PartRepository;
import com.miniplm.repository.PartVersionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BomService {

    private static final int MAX_DEPTH = 25;

    private final PartRepository partRepository;
    private final PartVersionRepository versionRepository;
    private final BomLinkRepository bomLinkRepository;

    // ---------- add / update / remove ----------

    @Transactional
    public BomNode addChild(Long partId, BomAddRequest req) {
        Part parent = findPart(partId);
        PartVersion parentVersion = editableLatest(partId);

        Part child = partRepository.findByPartNumber(req.childPartNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Child part not found: " + req.childPartNumber()));

        if (child.getId().equals(parent.getId())) {
            throw new BusinessRuleException("A part cannot contain itself.");
        }
        if (bomLinkRepository.existsByParentVersionIdAndChildPartId(parentVersion.getId(), child.getId())) {
            throw new BusinessRuleException(child.getPartNumber()
                    + " is already in this BOM. Update its quantity instead.");
        }
        // Circular check: does the child already contain the parent somewhere below it?
        if (reaches(child.getId(), parent.getId(), new HashSet<>())) {
            throw new BusinessRuleException("Circular BOM: " + child.getPartNumber()
                    + " already contains " + parent.getPartNumber() + " somewhere below it.");
        }

        BomLink link = new BomLink();
        link.setParentVersion(parentVersion);
        link.setChildPart(child);
        link.setQuantity(req.quantity());
        link.setUnit(req.unit() == null || req.unit().isBlank() ? "EA" : req.unit().trim());
        link = bomLinkRepository.save(link);
        parentVersion.getChildren().add(link);

        return flatNode(link, 1);
    }

    @Transactional
    public BomNode updateQuantity(Long partId, Long linkId, BomUpdateRequest req) {
        findPart(partId);
        PartVersion parentVersion = editableLatest(partId);
        BomLink link = findLink(parentVersion, linkId);
        link.setQuantity(req.quantity());
        return flatNode(bomLinkRepository.save(link), 1);
    }

    @Transactional
    public void removeChild(Long partId, Long linkId) {
        findPart(partId);
        PartVersion parentVersion = editableLatest(partId);
        BomLink link = findLink(parentVersion, linkId);
        parentVersion.getChildren().remove(link);
        bomLinkRepository.delete(link);
    }

    // ---------- read ----------

    /** Indented (multi-level) BOM of the part's latest revision. */
    @Transactional(readOnly = true)
    public BomNode getTree(Long partId) {
        Part root = findPart(partId);
        PartVersion version = latest(partId);
        return buildNode(null, root, version, 1, "EA", 0, new HashSet<>());
    }

    /** Which assemblies use this part. */
    @Transactional(readOnly = true)
    public List<WhereUsedResponse> whereUsed(Long partId) {
        findPart(partId);
        return bomLinkRepository.findByChildPartId(partId).stream()
                .map(link -> {
                    PartVersion pv = link.getParentVersion();
                    Part pp = pv.getPart();
                    return new WhereUsedResponse(pp.getId(), pp.getPartNumber(), pp.getName(),
                            pv.getRevision(), pv.getState().name(),
                            link.getQuantity(), link.getUnit());
                })
                .sorted(Comparator.comparing(WhereUsedResponse::partNumber)
                        .thenComparing(WhereUsedResponse::revision))
                .toList();
    }

    // ---------- Excel export ----------

    @Transactional(readOnly = true)
    public byte[] exportExcel(Long partId) {
        BomNode root = getTree(partId);
        List<BomNode> rows = new ArrayList<>();
        flatten(root, rows);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("BOM");

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);

            String[] titles = {"Level", "Part Number", "Name", "Revision", "State", "Qty", "Unit"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < titles.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(titles[i]);
                c.setCellStyle(header);
            }

            int r = 1;
            for (BomNode n : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(n.level());
                row.createCell(1).setCellValue("    ".repeat(n.level()) + n.partNumber());
                row.createCell(2).setCellValue(n.name());
                row.createCell(3).setCellValue(n.revision());
                row.createCell(4).setCellValue(n.state());
                row.createCell(5).setCellValue(n.quantity());
                row.createCell(6).setCellValue(n.unit());
            }
            for (int i = 0; i < titles.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the Excel file", e);
        }
    }

    // ---------- helpers ----------

    private BomNode buildNode(BomLink link, Part part, PartVersion version,
                              Integer qty, String unit, int level, Set<Long> ancestors) {
        List<BomNode> kids = new ArrayList<>();
        // Safety guards: stop on a loop or on an unreasonably deep tree
        if (level < MAX_DEPTH && ancestors.add(part.getId())) {
            for (BomLink l : version.getChildren()) {
                Part cp = l.getChildPart();
                PartVersion cv = latest(cp.getId());
                kids.add(buildNode(l, cp, cv, l.getQuantity(), l.getUnit(), level + 1, ancestors));
            }
            ancestors.remove(part.getId());
        }
        return new BomNode(link == null ? null : link.getId(), part.getId(), part.getPartNumber(),
                part.getName(), version.getRevision(), version.getState().name(),
                qty, unit, level, kids);
    }

    private BomNode flatNode(BomLink link, int level) {
        Part cp = link.getChildPart();
        PartVersion cv = latest(cp.getId());
        return new BomNode(link.getId(), cp.getId(), cp.getPartNumber(), cp.getName(),
                cv.getRevision(), cv.getState().name(),
                link.getQuantity(), link.getUnit(), level, List.of());
    }

    private void flatten(BomNode node, List<BomNode> out) {
        out.add(node);
        node.children().forEach(c -> flatten(c, out));
    }

    /** True if `target` appears anywhere in the BOM tree below `fromPartId`. */
    private boolean reaches(Long fromPartId, Long target, Set<Long> seen) {
        if (!seen.add(fromPartId)) {
            return false;
        }
        PartVersion v = versionRepository.findFirstByPartIdOrderByIdDesc(fromPartId).orElse(null);
        if (v == null) {
            return false;
        }
        for (BomLink l : v.getChildren()) {
            Long childId = l.getChildPart().getId();
            if (childId.equals(target) || reaches(childId, target, seen)) {
                return true;
            }
        }
        return false;
    }

    private BomLink findLink(PartVersion parentVersion, Long linkId) {
        return bomLinkRepository.findById(linkId)
                .filter(l -> l.getParentVersion().getId().equals(parentVersion.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOM line " + linkId + " not found in the current revision of this part"));
    }

    private PartVersion editableLatest(Long partId) {
        PartVersion v = latest(partId);
        if (!v.getState().isEditable()) {
            throw new BusinessRuleException("Revision " + v.getRevision() + " is " + v.getState()
                    + " and its BOM cannot be changed. Revise the part first.");
        }
        return v;
    }

    private PartVersion latest(Long partId) {
        return versionRepository.findFirstByPartIdOrderByIdDesc(partId)
                .orElseThrow(() -> new ResourceNotFoundException("No revisions found for part " + partId));
    }

    private Part findPart(Long id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Part not found: " + id));
    }
}
