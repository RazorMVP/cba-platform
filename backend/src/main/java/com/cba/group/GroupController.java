package com.cba.group;

import com.cba.common.response.ApiResponse;
import com.cba.group.dto.CollectionSheetRequest;
import com.cba.group.dto.GroupRequest;
import com.cba.group.dto.GroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Groups & Centers", description = "Microfinance group and center management, collection sheets, GLIM")
public class GroupController {

    private final GroupService groupService;

    // ── Groups ───────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Create a client group")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(@Valid @RequestBody GroupRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.createGroup(req)));
    }

    @GetMapping("/api/v1/groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List groups (optionally filter by officeId)")
    public ResponseEntity<ApiResponse<List<GroupResponse>>> listGroups(
            @RequestParam(required = false) UUID officeId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.listGroups(officeId)));
    }

    @GetMapping("/api/v1/groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get group details")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroup(id)));
    }

    @PostMapping("/api/v1/groups/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Activate a pending group")
    public ResponseEntity<ApiResponse<GroupResponse>> activateGroup(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.activateGroup(id)));
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/groups/{groupId}/members/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Add a customer to a group")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @PathVariable UUID groupId, @PathVariable UUID customerId) {
        groupService.addMember(groupId, customerId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/api/v1/groups/{groupId}/members/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Remove a customer from a group")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID groupId, @PathVariable UUID customerId) {
        groupService.removeMember(groupId, customerId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Staff Assignment ─────────────────────────────────────────────────────

    @PostMapping("/api/v1/groups/{groupId}/assignstaff")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a loan officer to a group (or change the current assignment)")
    public ResponseEntity<ApiResponse<GroupResponse>> assignStaff(
            @PathVariable UUID groupId, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.assignStaff(groupId, staffId)));
    }

    @DeleteMapping("/api/v1/groups/{groupId}/assignstaff")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove staff assignment from a group")
    public ResponseEntity<ApiResponse<GroupResponse>> unassignStaff(@PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.unassignStaff(groupId)));
    }

    // ── Collection Sheets ─────────────────────────────────────────────────────

    @PostMapping("/api/v1/collectionsheets")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Generate a collection sheet for a group meeting")
    public ResponseEntity<ApiResponse<CollectionSheet>> generateSheet(
            @Valid @RequestBody CollectionSheetRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.generateCollectionSheet(req)));
    }

    @GetMapping("/api/v1/groups/{groupId}/collectionsheets")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List collection sheets for a group")
    public ResponseEntity<ApiResponse<List<CollectionSheet>>> getGroupSheets(@PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroupSheets(groupId)));
    }

    // ── GLIM ──────────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/groups/{groupId}/glimaccounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List GLIM accounts for a group (individual loan tracking per member)")
    public ResponseEntity<ApiResponse<List<GlimAccount>>> getGlimAccounts(@PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGlimAccounts(groupId)));
    }
}
