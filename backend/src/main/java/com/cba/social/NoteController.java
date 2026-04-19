package com.cba.social;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Notes", description = "Polymorphic notes attached to any entity (clients, loans, accounts, etc.)")
@RestController
@RequestMapping("/api/v1/{entityType}/{entityId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @Operation(summary = "List notes for an entity")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Note>> list(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        return ApiResponse.ok(noteService.listNotes(entityType, entityId, pageable));
    }

    @Operation(summary = "Get a single note by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Note> get(@PathVariable String entityType,
                                  @PathVariable UUID entityId,
                                  @PathVariable UUID id) {
        return ApiResponse.ok(noteService.getNote(id));
    }

    @Operation(summary = "Add a note to an entity")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Note> create(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestBody NoteService.CreateNoteRequest req) {
        return ApiResponse.ok(noteService.createNote(entityType, entityId, req, null));
    }

    @Operation(summary = "Update a note")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Note> update(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @PathVariable UUID id,
            @RequestBody NoteService.CreateNoteRequest req) {
        return ApiResponse.ok(noteService.updateNote(id, req));
    }

    @Operation(summary = "Delete a note")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@PathVariable String entityType,
                       @PathVariable UUID entityId,
                       @PathVariable UUID id) {
        noteService.deleteNote(id);
    }
}
