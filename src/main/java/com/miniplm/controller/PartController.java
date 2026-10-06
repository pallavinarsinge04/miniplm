package com.miniplm.controller;

import com.miniplm.dto.PartRequest;
import com.miniplm.dto.PartResponse;
import com.miniplm.dto.VersionResponse;
import com.miniplm.service.PartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartService partService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartResponse create(@Valid @RequestBody PartRequest request) {
        return partService.create(request);
    }

    @GetMapping
    public List<PartResponse> list(@RequestParam(required = false) String q) {
        return partService.search(q);
    }

    @GetMapping("/{id}")
    public PartResponse get(@PathVariable Long id) {
        return partService.get(id);
    }

    @PutMapping("/{id}")
    public PartResponse update(@PathVariable Long id, @Valid @RequestBody PartRequest request) {
        return partService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        partService.delete(id);
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> versions(@PathVariable Long id) {
        return partService.versions(id);
    }
}
