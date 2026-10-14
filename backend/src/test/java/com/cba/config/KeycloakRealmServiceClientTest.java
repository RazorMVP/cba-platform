package com.cba.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Keycloak realm (compose file and the k8s ConfigMap copy) for the backend's
 * card-service credential: the client exists for client_credentials only, its secret is an
 * environment placeholder (this repository is public), and its service account holds
 * CARD_READER and nothing else — while no person holds CARD_READER.
 */
@DisplayName("Keycloak realm — cba-backend-service client and CARD_READER role")
class KeycloakRealmServiceClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode realm(String which) throws Exception {
        Path infra = Path.of("..", "infrastructure");
        if ("compose".equals(which)) {
            return JSON.readTree(Files.readString(infra.resolve("keycloak/cba-realm.json")));
        }
        // The ConfigMap embeds the same realm JSON as a block scalar under "cba-realm.json: |".
        List<String> lines = Files.readAllLines(infra.resolve("k8s/keycloak/keycloak-realm-configmap.yaml"));
        int start = lines.indexOf(lines.stream().filter(l -> l.trim().equals("cba-realm.json: |")).findFirst().orElseThrow());
        List<String> body = new ArrayList<>();
        for (String l : lines.subList(start + 1, lines.size())) body.add(l.length() >= 4 ? l.substring(4) : l.trim());
        return JSON.readTree(String.join("\n", body));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"compose", "k8s"})
    @DisplayName("the service client: client_credentials only, secret from the environment")
    void serviceClient(String which) throws Exception {
        JsonNode client = find(realm(which).get("clients"), "clientId", "cba-backend-service");
        assertThat(client.get("serviceAccountsEnabled").asBoolean()).isTrue();
        assertThat(client.get("publicClient").asBoolean()).isFalse();
        assertThat(client.get("standardFlowEnabled").asBoolean()).isFalse();
        assertThat(client.get("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(client.get("directAccessGrantsEnabled").asBoolean()).isFalse();
        assertThat(client.get("secret").asText()).as("never a literal secret in a public repo")
                .isEqualTo("${CBA_BACKEND_SERVICE_SECRET}");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"compose", "k8s"})
    @DisplayName("CARD_READER: defined, held by the service account alone, and by no person")
    void cardReaderRole(String which) throws Exception {
        JsonNode realm = realm(which);
        find(realm.get("roles").get("realm"), "name", "CARD_READER");

        JsonNode serviceAccount = find(realm.get("users"), "serviceAccountClientId", "cba-backend-service");
        assertThat(roles(serviceAccount)).containsExactly("CARD_READER");

        for (JsonNode user : realm.get("users")) {
            if (!user.has("serviceAccountClientId")) {
                assertThat(roles(user)).as(user.get("username").asText()).doesNotContain("CARD_READER");
            }
        }
    }

    private static List<String> roles(JsonNode user) {
        List<String> roles = new ArrayList<>();
        user.get("realmRoles").forEach(r -> roles.add(r.asText()));
        return roles;
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.path(field).asText())) return n;
        }
        throw new AssertionError(field + "=" + value + " not found in " + array.findValuesAsText(field).stream()
                .collect(Collectors.joining(", ")));
    }
}
