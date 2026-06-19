package com.cba.card.openbanking.apikey;

import com.cba.card.common.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link ApiKeyService} — SHA-256 key hashing, verification, revocation. */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository repository;
    @InjectMocks ApiKeyService service;

    @Test
    @DisplayName("SHA-256 matches the canonical vector for \"abc\"")
    void sha256Vector() {
        assertThat(ApiKeyService.sha256hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("issueKey returns a cba_-prefixed raw key and stores only its SHA-256 hash")
    void issueKeyHashesNotStoresRaw() {
        when(repository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyService.IssueResult result = service.issueKey("portal", UUID.randomUUID(), List.of("cards:read"));

        assertThat(result.rawKey()).startsWith("cba_");
        assertThat(result.apiKey().getKeyHash())
                .isEqualTo(ApiKeyService.sha256hex(result.rawKey()))
                .isNotEqualTo(result.rawKey()); // stored value is the hash, never the raw key
    }

    @Test
    @DisplayName("verify matches by SHA-256 hash and stamps last_used_at")
    void verifySuccess() {
        String rawKey = "cba_known_test_key";
        ApiKey stored = new ApiKey();
        when(repository.findByKeyHashAndActiveTrue(ApiKeyService.sha256hex(rawKey)))
                .thenReturn(Optional.of(stored));
        when(repository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        Optional<ApiKey> result = service.verify(rawKey);

        assertThat(result).isPresent();
        assertThat(stored.getLastUsedAt()).isNotNull();
        verify(repository).save(stored);
    }

    @Test
    @DisplayName("verify returns empty for an unknown key and does not write")
    void verifyUnknown() {
        when(repository.findByKeyHashAndActiveTrue(any())).thenReturn(Optional.empty());
        assertThat(service.verify("cba_bogus")).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("revoke deactivates the key")
    void revoke() {
        UUID id = UUID.randomUUID();
        ApiKey key = new ApiKey();
        key.setActive(true);
        when(repository.findById(id)).thenReturn(Optional.of(key));

        service.revoke(id);

        assertThat(key.isActive()).isFalse();
        verify(repository).save(key);
    }

    @Test
    @DisplayName("revoke throws when the key is not found")
    void revokeNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revoke(id)).isInstanceOf(CbaException.class);
    }
}
