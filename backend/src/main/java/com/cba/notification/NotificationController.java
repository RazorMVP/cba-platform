package com.cba.notification;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification template management and delivery history")
public class NotificationController {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;

    // ── Template management ───────────────────────────────────────────────────

    record TemplateRequest(
        String name,
        String eventType,
        NotificationTemplate.DeliveryMethod deliveryMethod,
        String subject,
        String body
    ) {}

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all notification templates")
    public ResponseEntity<ApiResponse<List<NotificationTemplate>>> listTemplates(
            @RequestParam(required = false) Boolean active) {
        List<NotificationTemplate> result = active != null && active
                ? templateRepository.findByActiveTrue()
                : templateRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a single notification template")
    public ResponseEntity<ApiResponse<NotificationTemplate>> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(findTemplateOrThrow(id)));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a notification template")
    public ResponseEntity<ApiResponse<NotificationTemplate>> createTemplate(
            @RequestBody TemplateRequest req) {
        NotificationTemplate t = new NotificationTemplate();
        applyRequest(t, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(templateRepository.save(t)));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a notification template")
    public ResponseEntity<ApiResponse<NotificationTemplate>> updateTemplate(
            @PathVariable UUID id, @RequestBody TemplateRequest req) {
        NotificationTemplate t = findTemplateOrThrow(id);
        applyRequest(t, req);
        return ResponseEntity.ok(ApiResponse.ok(templateRepository.save(t)));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate (soft-delete) a notification template")
    public ResponseEntity<ApiResponse<Void>> deactivateTemplate(@PathVariable UUID id) {
        NotificationTemplate t = findTemplateOrThrow(id);
        t.setActive(false);
        templateRepository.save(t);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Test notification ─────────────────────────────────────────────────────

    record TestRequest(UUID templateId, String recipientRef) {}

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send a test notification using a template (logs the attempt)")
    public ResponseEntity<ApiResponse<NotificationLog>> sendTest(@RequestBody TestRequest req) {
        NotificationTemplate t = findTemplateOrThrow(req.templateId());

        NotificationLog log = new NotificationLog();
        log.setTemplateId(t.getId());
        log.setEventType(t.getEventType());
        log.setDeliveryMethod(t.getDeliveryMethod());
        log.setRecipientRef(mask(req.recipientRef(), t.getDeliveryMethod()));
        log.setSentAt(OffsetDateTime.now());

        // In production, this is where SMTP/SMS gateway is called.
        // For dev, we log the attempt as SENT (MailHog catches the email).
        log.setStatus(NotificationLog.Status.SENT);

        return ResponseEntity.ok(ApiResponse.ok(logRepository.save(log)));
    }

    // ── Delivery history ──────────────────────────────────────────────────────

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List notification delivery history — filter by eventType or recipientId")
    public ResponseEntity<ApiResponse<Page<NotificationLog>>> listHistory(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID recipientId,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<NotificationLog> page;
        if (eventType != null) {
            page = logRepository.findByEventTypeOrderBySentAtDesc(eventType, pageable);
        } else if (recipientId != null) {
            page = logRepository.findByRecipientIdOrderBySentAtDesc(recipientId, pageable);
        } else {
            page = logRepository.findAllByOrderBySentAtDesc(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(page,
                ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NotificationTemplate findTemplateOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("NotificationTemplate", id.toString()));
    }

    private void applyRequest(NotificationTemplate t, TemplateRequest req) {
        t.setName(req.name());
        t.setEventType(req.eventType());
        t.setDeliveryMethod(req.deliveryMethod());
        t.setSubject(req.subject());
        t.setBody(req.body());
    }

    /** Masks email to a***@domain.com or phone to ****NNNN for logging. */
    private String mask(String ref, NotificationTemplate.DeliveryMethod method) {
        if (ref == null) return null;
        if (method == NotificationTemplate.DeliveryMethod.EMAIL) {
            int at = ref.indexOf('@');
            return at > 1 ? ref.charAt(0) + "***" + ref.substring(at) : "***";
        }
        return ref.length() > 4 ? "****" + ref.substring(ref.length() - 4) : "****";
    }
}
