package com.cba.card.bin;

import com.cba.card.common.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Tests for {@link BinService} — PAN → scheme routing via BIN range scan. */
@ExtendWith(MockitoExtension.class)
class BinServiceTest {

    @Mock BinRangeRepository binRangeRepository;
    @InjectMocks BinService service;

    private static BinRange range(String binStart, SchemeType scheme) {
        BinRange r = new BinRange();
        r.setBinStart(binStart);
        r.setScheme(scheme);
        r.setActive(true);
        return r;
    }

    @Test
    @DisplayName("a null PAN resolves to UNKNOWN")
    void nullPanUnknown() {
        assertThat(service.lookupScheme(null)).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("a PAN shorter than 6 digits resolves to UNKNOWN")
    void shortPanUnknown() {
        assertThat(service.lookupScheme("12345")).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("a matched BIN returns the range's scheme")
    void matchedBinReturnsScheme() {
        when(binRangeRepository.findByPan8("41111111")).thenReturn(List.of(range("41111111", SchemeType.VISA)));
        assertThat(service.lookupScheme("4111111111111111")).isEqualTo(SchemeType.VISA);
    }

    @Test
    @DisplayName("an unmatched BIN resolves to UNKNOWN")
    void unmatchedBinUnknown() {
        when(binRangeRepository.findByPan8(any())).thenReturn(List.of());
        assertThat(service.lookupScheme("4111111111111111")).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("getAllMappings exports binStart → scheme name")
    void getAllMappings() {
        when(binRangeRepository.findAllByActiveTrue()).thenReturn(List.of(
                range("411111", SchemeType.VISA),
                range("511111", SchemeType.MASTERCARD)));

        Map<String, String> mappings = service.getAllMappings();

        assertThat(mappings)
                .containsEntry("411111", "VISA")
                .containsEntry("511111", "MASTERCARD");
    }

    @Test
    @DisplayName("findById throws when the BIN range does not exist")
    void findByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(binRangeRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(CbaException.class);
    }
}
