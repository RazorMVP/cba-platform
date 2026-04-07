package com.cba.social;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/{entityType}/{entityId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Document>> list(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        return ApiResponse.ok(documentService.listDocuments(entityType, entityId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Document> get(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @PathVariable UUID id) {
        return ApiResponse.ok(documentService.getDocument(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Document> create(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestBody DocumentService.CreateDocumentRequest req) {
        return ApiResponse.ok(documentService.createDocument(entityType, entityId, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @PathVariable UUID id) {
        documentService.deleteDocument(id);
    }
}
