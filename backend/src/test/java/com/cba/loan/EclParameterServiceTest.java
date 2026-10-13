package com.cba.loan;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.loan.EclParameterService.PdRateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EclParameterService — PD table")
class EclParameterServiceTest {

    @Mock EclPdRateRepository pdRateRepository;
    @Mock LoanRepository loanRepository;
    @Mock RegulatoryReserveService regulatoryReserveService;
    @Mock AuditLogService auditLogService;
    @InjectMocks EclParameterService service;

    @Test
    @DisplayName("a complete, non-overlapping table is accepted and replaces the old one")
    void replace_valid() {
        List<PdRateRequest> rates = List.of(
                rate(1, 0, 30, "1.5"), rate(2, 31, 60, "10"), rate(2, 61, 90, "25"), rate(3, 91, 99999, "100"));

        assertThatCode(() -> service.replacePdRates(rates)).doesNotThrowAnyException();
        verify(pdRateRepository).deleteAllInBatch();
        verify(pdRateRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("every stage needs a band")
    void missingStage() {
        assertThatThrownBy(() -> EclParameterService.validate(List.of(rate(1, 0, 30, "2"), rate(2, 31, 90, "20"))))
                .isInstanceOf(CbaException.class).hasMessageContaining("Stage 3 has no PD band");
    }

    @Test
    @DisplayName("bands may not overlap within a stage")
    void overlap() {
        assertThatThrownBy(() -> EclParameterService.validate(List.of(
                rate(1, 0, 30, "2"), rate(2, 31, 60, "10"), rate(2, 60, 90, "25"), rate(3, 91, 99999, "100"))))
                .isInstanceOf(CbaException.class).hasMessageContaining("overlap at 60");
        verifyNoInteractions(pdRateRepository);
    }

    @Test
    @DisplayName("a band can't end before it starts")
    void inverted() {
        assertThatThrownBy(() -> EclParameterService.validate(List.of(
                rate(1, 30, 0, "2"), rate(2, 31, 90, "20"), rate(3, 91, 99999, "100"))))
                .isInstanceOf(CbaException.class).hasMessageContaining("ends before it starts");
    }

    private static PdRateRequest rate(int stage, int min, int max, String pd) {
        return new PdRateRequest(stage, min, max, new BigDecimal(pd), null);
    }
}
