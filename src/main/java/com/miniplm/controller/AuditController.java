package com.miniplm.controller;

import com.miniplm.dto.AuditResponse;
import com.miniplm.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /** With entityType and entityId: history of one item. Without: the latest 100 entries. */
    @GetMapping
    public List<AuditResponse> find(@RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) Long entityId) {
        return auditService.find(entityType, entityId);
    }
}
