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
@RequestMapping("/api/v1/{entityType}/{entityId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Note>> list(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        return ApiResponse.ok(noteService.listNotes(entityType, entityId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Note> get(@PathVariable String entityType,
                                  @PathVariable UUID entityId,
                                  @PathVariable UUID id) {
        return ApiResponse.ok(noteService.getNote(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Note> create(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestBody NoteService.CreateNoteRequest req) {
        return ApiResponse.ok(noteService.createNote(entityType, entityId, req, null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Note> update(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @PathVariable UUID id,
            @RequestBody NoteService.CreateNoteRequest req) {
        return ApiResponse.ok(noteService.updateNote(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@PathVariable String entityType,
                       @PathVariable UUID entityId,
                       @PathVariable UUID id) {
        noteService.deleteNote(id);
    }
}
