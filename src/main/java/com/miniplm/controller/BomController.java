package com.miniplm.controller;

import com.miniplm.dto.BomAddRequest;
import com.miniplm.dto.BomNode;
import com.miniplm.dto.BomUpdateRequest;
import com.miniplm.dto.WhereUsedResponse;
import com.miniplm.service.BomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parts/{partId}")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    @PostMapping("/bom")
    @ResponseStatus(HttpStatus.CREATED)
    public BomNode add(@PathVariable Long partId, @Valid @RequestBody BomAddRequest request) {
        return bomService.addChild(partId, request);
    }

    @GetMapping("/bom")
    public BomNode tree(@PathVariable Long partId) {
        return bomService.getTree(partId);
    }

    @PutMapping("/bom/{linkId}")
    public BomNode updateQuantity(@PathVariable Long partId, @PathVariable Long linkId,
                                  @Valid @RequestBody BomUpdateRequest request) {
        return bomService.updateQuantity(partId, linkId, request);
    }

    @DeleteMapping("/bom/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long partId, @PathVariable Long linkId) {
        bomService.removeChild(partId, linkId);
    }

    @GetMapping("/where-used")
    public List<WhereUsedResponse> whereUsed(@PathVariable Long partId) {
        return bomService.whereUsed(partId);
    }

    @GetMapping("/bom/export")
    public ResponseEntity<byte[]> export(@PathVariable Long partId) {
        byte[] file = bomService.exportExcel(partId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bom-" + partId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }
}
