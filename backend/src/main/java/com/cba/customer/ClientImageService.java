package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientImageService {

    public record SaveImageRequest(
        String location,
        ClientImage.StorageType storageType,
        String contentType,
        Long size
    ) {}

    private final ClientImageRepository imageRepository;
    private final AuditLogService       auditLogService;

    @Transactional(readOnly = true)
    public ClientImage getImage(UUID customerId) {
        return imageRepository.findByCustomerId(customerId)
            .orElseThrow(() -> CbaException.notFound("ClientImage", customerId));
    }

    @Transactional
    public ClientImage saveImage(UUID customerId, SaveImageRequest req) {
        ClientImage img = imageRepository.findByCustomerId(customerId)
            .orElseGet(() -> {
                ClientImage n = new ClientImage();
                n.setCustomerId(customerId);
                return n;
            });
        img.setLocation(req.location());
        img.setStorageType(req.storageType() != null ? req.storageType() : ClientImage.StorageType.FILE_SYSTEM);
        img.setContentType(req.contentType());
        img.setSize(req.size());
        ClientImage saved = imageRepository.save(img);
        auditLogService.log("ClientImage", customerId.toString(), "UPSERT", null, saved);
        return saved;
    }

    @Transactional
    public void deleteImage(UUID customerId) {
        ClientImage img = getImage(customerId);
        imageRepository.delete(img);
        auditLogService.log("ClientImage", customerId.toString(), "DELETE", null, null);
    }
}
