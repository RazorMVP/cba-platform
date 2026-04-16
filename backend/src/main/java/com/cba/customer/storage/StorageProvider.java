package com.cba.customer.storage;

import java.util.UUID;

public interface StorageProvider {

    StorageResult store(UUID customerId, String fileName, String contentType, byte[] data);

    byte[] retrieve(String location);

    void delete(String location);

    String getType();

    record StorageResult(String location, String storageType) {}
}
