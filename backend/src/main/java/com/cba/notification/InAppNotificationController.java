package com.cba.notification;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "In-App Notifications", description = "Global notification feed, unread count, mark-all-read, and push device registration")
public class InAppNotificationController {

    private final InAppNotificationService service;
    private final PushDispatchService pushDispatchService;

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "List notifications (newest first, paginated)")
    public ResponseEntity<ApiResponse<List<InAppNotification>>> inbox(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<InAppNotification> page = service.getNotifications(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page.getContent(),
                ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @GetMapping("/inbox/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "Unread notification count for the calling user")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        long count = service.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCountResponse(count)));
    }

    @PostMapping("/inbox/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "Mark all notifications as read for the calling user")
    public ResponseEntity<ApiResponse<Void>> markAllRead(Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        service.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Push device endpoints

    @PostMapping("/devices")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "Register or refresh an FCM push device token")
    public ResponseEntity<ApiResponse<PushDevice>> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest req,
            Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        PushDevice device = service.registerDevice(userId, req.fcmToken(), req.platform(), req.deviceLabel());
        return ResponseEntity.ok(ApiResponse.ok(device));
    }

    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "Deregister (soft-delete) a push device")
    public ResponseEntity<ApiResponse<Void>> deregisterDevice(
            @PathVariable UUID deviceId,
            Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        service.deregisterDevice(userId, deviceId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "List push devices registered by the calling user")
    public ResponseEntity<ApiResponse<List<PushDevice>>> listDevices(Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        return ResponseEntity.ok(ApiResponse.ok(service.getDevices(userId)));
    }

    @PostMapping("/push")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send a push notification to a user's devices",
        description = "Fans out to all active devices of the target user via the active push provider "
            + "(NONE=simulated in dev; HTTP=real relay). Dead tokens are auto-deactivated. "
            + "Returns { total, sent, failed, deactivated, provider }.")
    public ResponseEntity<ApiResponse<PushDispatchService.PushDispatchResult>> sendPush(
            @Valid @RequestBody SendPushRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                pushDispatchService.sendToUser(req.userId(), req.title(), req.body(), req.data())));
    }

    // DTOs

    public record UnreadCountResponse(long count) {}

    public record RegisterDeviceRequest(
            @NotBlank String fcmToken,
            @NotNull PushDevice.Platform platform,
            String deviceLabel) {}

    public record SendPushRequest(
            @NotBlank String userId,
            @NotBlank String title,
            @NotBlank String body,
            java.util.Map<String, String> data) {}
}
