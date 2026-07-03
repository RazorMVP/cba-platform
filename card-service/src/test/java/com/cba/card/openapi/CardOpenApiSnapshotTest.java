package com.cba.card.openapi;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * OpenAPI Contract Snapshot Test — card-service
 *
 * Calls /v3/api-docs.yaml after a full Spring Boot + Testcontainers startup
 * and compares the live spec against the committed snapshot at
 * card-service/docs/openapi-snapshot.yaml.
 *
 * How it works:
 *   - First run (snapshot file missing or placeholder): writes the snapshot and passes.
 *     Commit the generated file.
 *   - Subsequent runs: compares live spec to snapshot; fails with a diff message if
 *     the spec has changed without a snapshot update.
 *   - To regenerate: run with -Dupdate.api.snapshot=true, commit the result.
 *
 * This ensures that every card-service REST endpoint addition or removal is
 * deliberate and that the committed snapshot always reflects the running API.
 */
class CardOpenApiSnapshotTest extends AbstractCardIntegrationTest {

    /** Path is relative to the card-service module root. */
    private static final Path SNAPSHOT_PATH = Paths.get("docs", "openapi-snapshot.yaml");

    /** Placeholder content written on first run; replaced with real spec. */
    private static final String PLACEHOLDER_MARKER = "# openapi-snapshot-placeholder";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void cardApiSpecMatchesSnapshot() throws IOException {
        // Normalize the only environment-specific token: springdoc emits the live
        // servers[].url with the @SpringBootTest(RANDOM_PORT) port, which differs every
        // run. Without this, the snapshot could only ever match the exact run that wrote
        // it — a latent non-determinism that reddened every CI full-integration build.
        String liveSpec = normalize(fetchLiveSpec());

        boolean updateMode = Boolean.parseBoolean(
                System.getProperty("update.api.snapshot", "false"));
        boolean snapshotMissing = !Files.exists(SNAPSHOT_PATH)
                || isPlaceholder(SNAPSHOT_PATH);

        if (updateMode || snapshotMissing) {
            writeSnapshot(liveSpec);
            if (snapshotMissing) {
                System.out.println(
                    "[CardOpenApiSnapshotTest] Snapshot generated at: " + SNAPSHOT_PATH.toAbsolutePath()
                    + " — commit this file to lock the card-service API contract.");
            } else {
                System.out.println(
                    "[CardOpenApiSnapshotTest] Snapshot updated at: " + SNAPSHOT_PATH.toAbsolutePath()
                    + " — review the diff and commit.");
            }
            return; // Pass on first-write / explicit update
        }

        String committedSpec = Files.readString(SNAPSHOT_PATH, StandardCharsets.UTF_8);

        if (!liveSpec.equals(committedSpec)) {
            fail(buildDiffMessage(committedSpec, liveSpec));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Replace the random local server port so the snapshot is stable across runs. */
    private static String normalize(String spec) {
        return spec.replaceAll("http://localhost:\\d+", "http://localhost:PORT");
    }

    private String fetchLiveSpec() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/v3/api-docs.yaml", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("GET /v3/api-docs.yaml should return 200")
                .isTrue();
        assertThat(response.getBody())
                .as("/v3/api-docs.yaml must not be empty")
                .isNotBlank();
        return response.getBody();
    }

    private boolean isPlaceholder(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8).trim();
        return content.isEmpty() || content.startsWith(PLACEHOLDER_MARKER);
    }

    private void writeSnapshot(String spec) throws IOException {
        Files.createDirectories(SNAPSHOT_PATH.getParent());
        Files.writeString(SNAPSHOT_PATH, spec, StandardCharsets.UTF_8);
    }

    private String buildDiffMessage(String committed, String live) {
        String[] committedLines = committed.split("\n");
        String[] liveLines = live.split("\n");

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║  card-service OpenAPI snapshot mismatch!                ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
        sb.append("The live spec differs from the committed snapshot at:\n");
        sb.append("  card-service/").append(SNAPSHOT_PATH).append("\n\n");
        sb.append("To update the snapshot, run:\n");
        sb.append("  cd card-service && ./mvnw verify -Pfull-integration -Dupdate.api.snapshot=true\n\n");
        sb.append("Then review the diff and commit docs/openapi-snapshot.yaml.\n\n");

        // Show first 10 differing lines for quick diagnosis
        sb.append("First differences (committed → live):\n");
        int shown = 0;
        int maxLine = Math.max(committedLines.length, liveLines.length);
        for (int i = 0; i < maxLine && shown < 10; i++) {
            String c = i < committedLines.length ? committedLines[i] : "<missing>";
            String l = i < liveLines.length ? liveLines[i] : "<missing>";
            if (!c.equals(l)) {
                sb.append(String.format("  Line %4d ─ committed: %s%n", i + 1, c));
                sb.append(String.format("           ─     live: %s%n", l));
                shown++;
            }
        }
        if (shown == 10) {
            sb.append("  ... (more differences omitted)\n");
        }
        return sb.toString();
    }
}
