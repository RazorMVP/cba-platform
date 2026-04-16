package com.cba.customer.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

// When storage=DATABASE the image bytes are written directly into client_images.data (BYTEA).
// The "location" field is set to the customerId string — a stable back-reference.
@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "DATABASE")
public class DatabaseStorageProvider implements StorageProvider {

    @Override
    public StorageResult store(UUID customerId, String fileName, String contentType, byte[] data) {
        // Actual persistence happens in ClientImageService which has access to the entity.
        // This provider just signals the storage type; the byte[] is attached to the entity directly.
        return new StorageResult("db:" + customerId, "DATABASE");
    }

    @Override
    public byte[] retrieve(String location) {
        // Bytes are fetched from the entity by ClientImageService — this path is never called.
        throw new UnsupportedOperationException("DatabaseStorageProvider: use ClientImageService.getImageData()");
    }

    @Override
    public void delete(String location) {
        // Deletion is handled by JPA cascade when the ClientImage entity is removed.
    }

    @Override
    public String getType() {
        return "DATABASE";
    }
}
