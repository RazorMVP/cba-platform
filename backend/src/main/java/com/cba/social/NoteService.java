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
public class NoteService {

    public record CreateNoteRequest(String note) {}

    private final NoteRepository noteRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<Note> listNotes(String entityType, UUID entityId, Pageable p) {
        return noteRepository.findByEntityTypeAndEntityId(entityType, entityId, p);
    }

    @Transactional(readOnly = true)
    public Note getNote(UUID id) {
        return noteRepository.findById(id).orElseThrow(() -> CbaException.notFound("Note", id));
    }

    @Transactional
    public Note createNote(String entityType, UUID entityId, CreateNoteRequest req, UUID createdByUserId) {
        Note note = new Note();
        note.setEntityType(entityType);
        note.setEntityId(entityId);
        note.setNote(req.note());
        note.setCreatedByUserId(createdByUserId);
        Note saved = noteRepository.save(note);
        auditLogService.log("Note", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Note updateNote(UUID id, CreateNoteRequest req) {
        Note note = getNote(id);
        note.setNote(req.note());
        Note saved = noteRepository.save(note);
        auditLogService.log("Note", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteNote(UUID id) {
        Note note = getNote(id);
        noteRepository.delete(note);
        auditLogService.log("Note", id.toString(), "DELETE", null, null);
    }
}
