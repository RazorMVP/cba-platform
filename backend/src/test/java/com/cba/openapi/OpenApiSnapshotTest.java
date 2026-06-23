package com.cba.openapi;

import com.cba.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * OpenAPI Contract Snapshot Test
 *
 * Calls /v3/api-docs after a full Spring Boot + Testcontainers startup
 * and compares the live spec against the committed snapshot at
 * backend/docs/openapi-snapshot.yaml.
 *
 * How it works:
 *   - First run (snapshot file missing or empty placeholder): writes the snapshot
 *     file and passes. Commit the generated file.
 *   - Subsequent runs: compares live spec to snapshot; fails with a diff message
 *     if the spec has changed without a snapshot update.
 *   - To regenerate: run with -Dupdate.api.snapshot=true, commit the result.
 *
 * This ensures that every REST endpoint addition or removal is deliberate
 * and that the committed snapshot always reflects the actual running API.
 */
class OpenApiSnapshotTest extends AbstractIntegrationTest {

    /** Path is relative to the project root (backend/). */
    private static final Path SNAPSHOT_PATH = Paths.get("docs", "openapi-snapshot.yaml");

    /** Placeholder content written on first run; replaced with real spec. */
    private static final String PLACEHOLDER_MARKER = "# openapi-snapshot-placeholder";

    /** YAML mapper used only to canonicalise specs for an order-insensitive comparison. */
    private static final YAMLMapper YAML = new YAMLMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiSpecMatchesSnapshot() throws IOException {
        String liveSpec = canonicalize(fetchLiveSpec());

        boolean updateMode = Boolean.parseBoolean(
                System.getProperty("update.api.snapshot", "false"));
        boolean snapshotMissing = !Files.exists(SNAPSHOT_PATH)
                || isPlaceholder(SNAPSHOT_PATH);

        if (updateMode || snapshotMissing) {
            writeSnapshot(liveSpec);
            if (snapshotMissing) {
                System.out.println(
                    "[OpenApiSnapshotTest] Snapshot generated at: " + SNAPSHOT_PATH.toAbsolutePath()
                    + " — commit this file to lock the API contract.");
            } else {
                System.out.println(
                    "[OpenApiSnapshotTest] Snapshot updated at: " + SNAPSHOT_PATH.toAbsolutePath()
                    + " — review the diff and commit.");
            }
            return; // Pass on first-write / explicit update
        }

        String committedSpec = canonicalize(Files.readString(SNAPSHOT_PATH, StandardCharsets.UTF_8));

        if (!liveSpec.equals(committedSpec)) {
            fail(buildDiffMessage(committedSpec, liveSpec));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Canonicalises an OpenAPI YAML document so the comparison is immune to springdoc's
     * non-deterministic map ordering. springdoc builds paths/schemas/properties via
     * reflection, whose order is not stable run-to-run (e.g. {@code totalElements} vs
     * {@code totalPages}); its {@code writer-with-order-by-keys} flag only sorts paths,
     * not schema properties (springdoc-openapi#1690 / #1362). We parse the spec into a
     * tree and recursively sort every object's keys. Array order is deliberately
     * PRESERVED — it is semantically meaningful in OpenAPI (parameter order, enum
     * values, {@code required}, {@code servers}, …), so re-ordering it could mask a real
     * contract change.
     */
    private String canonicalize(String yaml) {
        try {
            return YAML.writeValueAsString(sortKeys(YAML.readTree(yaml)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to canonicalize OpenAPI spec", e);
        }
    }

    private JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                sorted.set(name, sortKeys(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode arr = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> arr.add(sortKeys(child)));
            return arr;
        }
        return node;
    }

    private String fetchLiveSpec() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api-docs.yaml", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("GET /api-docs.yaml should return 200")
                .isTrue();
        assertThat(response.getBody())
                .as("/api-docs.yaml must not be empty")
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
        sb.append("║  OpenAPI snapshot mismatch — API surface has changed!   ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
        sb.append("The live spec differs from the committed snapshot at:\n");
        sb.append("  backend/").append(SNAPSHOT_PATH).append("\n\n");
        sb.append("To update the snapshot, run:\n");
        sb.append("  cd backend && ./mvnw verify -Pfull-integration -Dupdate.api.snapshot=true\n\n");
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
