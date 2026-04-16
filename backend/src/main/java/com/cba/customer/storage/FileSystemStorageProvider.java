package com.cba.customer.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "FILE_SYSTEM", matchIfMissing = true)
public class FileSystemStorageProvider implements StorageProvider {

    private final Path uploadDir;

    public FileSystemStorageProvider(@Value("${app.image.upload-dir:./uploads/customer-images}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create image upload directory: " + this.uploadDir, e);
        }
    }

    @Override
    public StorageResult store(UUID customerId, String fileName, String contentType, byte[] data) {
        try {
            String stored = customerId + "_" + System.currentTimeMillis() + "_" + sanitize(fileName);
            Path target = uploadDir.resolve(stored);
            Files.write(target, data);
            return new StorageResult(target.toString(), "FILE_SYSTEM");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store image", e);
        }
    }

    @Override
    public byte[] retrieve(String location) {
        try {
            return Files.readAllBytes(Paths.get(location));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read image from disk: " + location, e);
        }
    }

    @Override
    public void delete(String location) {
        try {
            Files.deleteIfExists(Paths.get(location));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete image: " + location, e);
        }
    }

    @Override
    public String getType() {
        return "FILE_SYSTEM";
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
