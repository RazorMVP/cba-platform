package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    public record CreateDocumentRequest(
        String name,
        String description,
        String fileName,
        Long fileSize,
        String contentType,
        String storagePath
    ) {}

    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<Document> listDocuments(String entityType, UUID entityId, Pageable p) {
        return documentRepository.findByEntityTypeAndEntityId(entityType, entityId, p);
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Document", id));
    }

    @Transactional
    public Document createDocument(String entityType, UUID entityId, CreateDocumentRequest req) {
        Document doc = new Document();
        doc.setEntityType(entityType);
        doc.setEntityId(entityId);
        doc.setName(req.name());
        doc.setDescription(req.description());
        doc.setFileName(req.fileName());
        doc.setFileSize(req.fileSize());
        doc.setContentType(req.contentType());
        doc.setStoragePath(req.storagePath());
        Document saved = documentRepository.save(doc);
        auditLogService.log("Document", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = getDocument(id);
        documentRepository.delete(doc);
        auditLogService.log("Document", id.toString(), "DELETE", null, null);
    }
}
