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

@Tag(name = "SMS Campaigns", description = "Bulk SMS campaign management — individual, broadcast and query-based campaigns with scheduled or triggered delivery")
@RestController
@RequiredArgsConstructor
public class SmsCampaignController {

    private final SmsCampaignService smsCampaignService;

    @Operation(summary = "List SMS campaigns")
    @GetMapping("/api/v1/smscampaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<SmsCampaign>> list(Pageable pageable) {
        return ApiResponse.ok(smsCampaignService.listCampaigns(pageable));
    }

    @Operation(summary = "Get an SMS campaign by ID")
    @GetMapping("/api/v1/smscampaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> get(@PathVariable UUID id) {
        return ApiResponse.ok(smsCampaignService.getCampaign(id));
    }

    @Operation(summary = "Create a new SMS campaign")
    @PostMapping("/api/v1/smscampaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> create(@RequestBody SmsCampaignService.CreateCampaignRequest req) {
        return ApiResponse.ok(smsCampaignService.createCampaign(req));
    }

    @Operation(summary = "Update an SMS campaign")
    @PutMapping("/api/v1/smscampaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> update(@PathVariable UUID id,
                                            @RequestBody SmsCampaignService.CreateCampaignRequest req) {
        return ApiResponse.ok(smsCampaignService.updateCampaign(id, req));
    }

    @Operation(summary = "Execute a campaign command (?command=activate)")
    @PostMapping("/api/v1/smscampaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> command(@PathVariable UUID id,
                                             @RequestParam String command) {
        if ("activate".equalsIgnoreCase(command)) {
            return ApiResponse.ok(smsCampaignService.activate(id));
        }
        throw new com.cba.common.exception.CbaException(
            "UNKNOWN_COMMAND", "Unknown command: " + command,
            org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Delete (soft-delete) an SMS campaign")
    @DeleteMapping("/api/v1/smscampaigns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        smsCampaignService.deleteCampaign(id);
    }

    @Operation(summary = "Send a campaign to an explicit recipient list",
        description = "Dispatches the campaign message to each recipient through the active SMS provider "
            + "(NONE = simulated in dev/sandbox; HTTP = real gateway). Returns per-status counts.")
    @PostMapping("/api/v1/smscampaigns/{id}/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaignService.SendResult> send(
            @PathVariable UUID id,
            @RequestBody SmsCampaignService.SendCampaignRequest req) {
        return ApiResponse.ok(smsCampaignService.sendCampaign(id, req));
    }

    @Operation(summary = "List delivery messages for an SMS campaign")
    @GetMapping("/api/v1/smscampaigns/{id}/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<SmsMessage>> messages(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(smsCampaignService.listMessages(id, pageable));
    }
}
