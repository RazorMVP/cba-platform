package com.cba.customer;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_images")
@Getter @Setter @NoArgsConstructor
public class ClientImage {

    public enum StorageType { FILE_SYSTEM, S3, DATABASE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(length = 500)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType = StorageType.FILE_SYSTEM;

    @Column(name = "content_type", length = 100)
    private String contentType;

    private Long size;

    // Only populated when storageType = DATABASE
    @Column(name = "data", columnDefinition = "BYTEA")
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
