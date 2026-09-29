package com.cba.customer.storage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test for {@link S3StorageProvider} against a real S3-compatible
 * server — exercising the actual AWS SDK v2 client, {@code forcePathStyle},
 * {@code endpointOverride}, and a genuine PUT/GET/DELETE round trip over the network.
 *
 * <p>This is the S3 path a production deployment uses with {@code app.image.storage=S3}
 * (MinIO / GCS-S3 / real S3). The unit tests never touch the SDK; this proves the client
 * config, credentials, and byte round-trip actually work.
 */
@Testcontainers
@DisplayName("S3StorageProvider — end-to-end against an S3-compatible container (S3Mock)")
class S3StorageProviderIntegrationTest {

    private static final String BUCKET = "cba-images";
    private static final String ACCESS = "test-access-key";
    private static final String SECRET = "test-secret-key";

    @Container
    static final GenericContainer<?> S3 =
            // Adobe S3Mock (Apache-2.0, Docker Hub), not MinIO. MinIO withdrew its free
            // images twice: Docker Hub `minio/minio` ("pull access denied", Session 125
            // cont. 9), then quay.io/minio/minio started answering anonymous pulls with
            // 401 UNAUTHORIZED (cont. 19). It only passed locally from a cached image;
            // every CI runner failed after the 420 s pull timeout. S3Mock implements the
            // S3 REST API the SDK speaks and accepts any credentials. Pinned, not `latest`.
            new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                    .withExposedPorts(9090)
                    .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    private static String endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
    }

    @BeforeAll
    static void createBucket() {
        try (S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS, SECRET)))
                .build()) {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test
    @DisplayName("store → retrieve → delete round-trips real bytes through an S3 endpoint")
    void storeRetrieveDelete() {
        S3StorageProvider provider = new S3StorageProvider(BUCKET, "us-east-1", ACCESS, SECRET, endpoint());
        UUID customerId = UUID.randomUUID();
        byte[] data = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

        StorageProvider.StorageResult stored = provider.store(customerId, "photo.jpg", "image/jpeg", data);

        assertThat(stored.storageType()).isEqualTo("S3");
        assertThat(stored.location()).contains(customerId.toString()).endsWith("_photo.jpg");

        byte[] fetched = provider.retrieve(stored.location());
        assertThat(fetched).isEqualTo(data);

        provider.delete(stored.location());
        assertThatThrownBy(() -> provider.retrieve(stored.location()))
                .isInstanceOf(NoSuchKeyException.class);
    }
}
