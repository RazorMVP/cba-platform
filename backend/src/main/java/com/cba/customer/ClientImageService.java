package com.cba.customer;

import com.cba.common.exception.CbaException;
import com.cba.customer.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientImageService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png");

    private final ClientImageRepository imageRepository;
    private final StorageProvider storageProvider;

    public record ImageMeta(boolean hasImage, String contentType, Long size, String fileName) {}

    @Transactional(readOnly = true)
    public ImageMeta getMeta(UUID customerId) {
        return imageRepository.findByCustomerId(customerId)
                .map(img -> new ImageMeta(true, img.getContentType(), img.getSize(), img.getFileName()))
                .orElse(new ImageMeta(false, null, null, null));
    }

    @Transactional(readOnly = true)
    public boolean hasImage(UUID customerId) {
        return imageRepository.findByCustomerId(customerId).isPresent();
    }

    @Transactional
    public ClientImage saveImage(UUID customerId, MultipartFile file) {
        validate(file);
        try {
            byte[] bytes = file.getBytes();
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";

            StorageProvider.StorageResult result =
                    storageProvider.store(customerId, originalName, file.getContentType(), bytes);

            Optional<ClientImage> existing = imageRepository.findByCustomerId(customerId);
            ClientImage img = existing.orElseGet(() -> {
                ClientImage i = new ClientImage();
                i.setCustomerId(customerId);
                return i;
            });

            // Clean up old external file when replacing
            if (existing.isPresent() && img.getLocation() != null
                    && img.getStorageType() != ClientImage.StorageType.DATABASE) {
                try { storageProvider.delete(img.getLocation()); } catch (Exception ignored) {}
            }

            img.setFileName(originalName);
            img.setLocation(result.location());
            img.setStorageType(ClientImage.StorageType.valueOf(result.storageType()));
            img.setContentType(file.getContentType());
            img.setSize(file.getSize());
            img.setData("DATABASE".equals(result.storageType()) ? bytes : null);

            return imageRepository.save(img);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] getImageData(UUID customerId) {
        ClientImage img = imageRepository.findByCustomerId(customerId)
                .orElseThrow(() -> CbaException.notFound("IMAGE_NOT_FOUND", "No image for customer " + customerId));

        if (img.getStorageType() == ClientImage.StorageType.DATABASE) {
            if (img.getData() == null) {
                throw CbaException.notFound("IMAGE_NOT_FOUND", "Image data missing in database");
            }
            return img.getData();
        }
        return storageProvider.retrieve(img.getLocation());
    }

    @Transactional
    public void deleteImage(UUID customerId) {
        ClientImage img = imageRepository.findByCustomerId(customerId)
                .orElseThrow(() -> CbaException.notFound("IMAGE_NOT_FOUND", "No image for customer " + customerId));
        if (img.getStorageType() != ClientImage.StorageType.DATABASE && img.getLocation() != null) {
            try { storageProvider.delete(img.getLocation()); } catch (Exception ignored) {}
        }
        imageRepository.delete(img);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CbaException.badRequest("IMAGE_EMPTY", "Image file must not be empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw CbaException.badRequest("IMAGE_TOO_LARGE", "Image must be 5 MB or smaller");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct)) {
            throw CbaException.badRequest("IMAGE_INVALID_TYPE", "Only JPEG and PNG images are accepted");
        }
    }
}
