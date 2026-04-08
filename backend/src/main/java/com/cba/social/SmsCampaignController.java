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
@RequiredArgsConstructor
public class SmsCampaignController {

    private final SmsCampaignService smsCampaignService;

    @GetMapping("/api/v1/smscampaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<SmsCampaign>> list(Pageable pageable) {
        return ApiResponse.ok(smsCampaignService.listCampaigns(pageable));
    }

    @GetMapping("/api/v1/smscampaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> get(@PathVariable UUID id) {
        return ApiResponse.ok(smsCampaignService.getCampaign(id));
    }

    @PostMapping("/api/v1/smscampaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> create(@RequestBody SmsCampaignService.CreateCampaignRequest req) {
        return ApiResponse.ok(smsCampaignService.createCampaign(req));
    }

    @PutMapping("/api/v1/smscampaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmsCampaign> update(@PathVariable UUID id,
                                            @RequestBody SmsCampaignService.CreateCampaignRequest req) {
        return ApiResponse.ok(smsCampaignService.updateCampaign(id, req));
    }

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

    @DeleteMapping("/api/v1/smscampaigns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        smsCampaignService.deleteCampaign(id);
    }

    @GetMapping("/api/v1/smscampaigns/{id}/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<SmsMessage>> messages(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(smsCampaignService.listMessages(id, pageable));
    }
}
