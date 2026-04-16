package com.cba.customer.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "S3")
public class S3StorageProvider implements StorageProvider {

    private final S3Client s3;
    private final String bucket;

    public S3StorageProvider(
            @Value("${app.image.s3.bucket}") String bucket,
            @Value("${app.image.s3.region:us-east-1}") String region,
            @Value("${app.image.s3.access-key:}") String accessKey,
            @Value("${app.image.s3.secret-key:}") String secretKey,
            @Value("${app.image.s3.endpoint-override:}") String endpointOverride) {

        this.bucket = bucket;
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        // endpointOverride enables MinIO, GCS S3-compatible, Localstack, etc.
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
            builder.forcePathStyle(true);
        }
        this.s3 = builder.build();
    }

    @Override
    public StorageResult store(UUID customerId, String fileName, String contentType, byte[] data) {
        String key = "customer-images/" + customerId + "/" + System.currentTimeMillis() + "_" + fileName;
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(data));
        return new StorageResult(key, "S3");
    }

    @Override
    public byte[] retrieve(String location) {
        return s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(location).build()).asByteArray();
    }

    @Override
    public void delete(String location) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(location).build());
    }

    @Override
    public String getType() {
        return "S3";
    }
}
