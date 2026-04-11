package com.cba.card.interchange;

import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterchangeService {

    private final InterchangeRateRepository    rateRepo;
    private final SchemeFeeRepository          feeRepo;
    private final InterchangeLogRepository     logRepo;
    private final InterchangeQualificationEngine engine;

    // ── Interchange Rates CRUD ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterchangeRate> listRates() {
        return rateRepo.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public InterchangeRate getRate(UUID id) {
        return rateRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("RATE_NOT_FOUND",
                        "Interchange rate not found: " + id));
    }

    @Transactional
    public InterchangeRate createRate(InterchangeRateRequest req) {
        InterchangeRate rate = new InterchangeRate();
        applyRateRequest(rate, req);
        return rateRepo.save(rate);
    }

    @Transactional
    public InterchangeRate updateRate(UUID id, InterchangeRateRequest req) {
        InterchangeRate rate = rateRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("RATE_NOT_FOUND",
                        "Interchange rate not found: " + id));
        applyRateRequest(rate, req);
        return rateRepo.save(rate);
    }

    @Transactional
    public void deleteRate(UUID id) {
        InterchangeRate rate = rateRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("RATE_NOT_FOUND",
                        "Interchange rate not found: " + id));
        rate.setActive(false);
        rateRepo.save(rate);
    }

    // ── Scheme Fees CRUD ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SchemeFee> listFees() {
        return feeRepo.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public SchemeFee getFee(UUID id) {
        return feeRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("FEE_NOT_FOUND",
                        "Scheme fee not found: " + id));
    }

    @Transactional
    public SchemeFee createFee(SchemeFeeRequest req) {
        SchemeFee fee = new SchemeFee();
        applyFeeRequest(fee, req);
        return feeRepo.save(fee);
    }

    @Transactional
    public SchemeFee updateFee(UUID id, SchemeFeeRequest req) {
        SchemeFee fee = feeRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("FEE_NOT_FOUND",
                        "Scheme fee not found: " + id));
        applyFeeRequest(fee, req);
        return feeRepo.save(fee);
    }

    @Transactional
    public void deleteFee(UUID id) {
        SchemeFee fee = feeRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("FEE_NOT_FOUND",
                        "Scheme fee not found: " + id));
        fee.setActive(false);
        feeRepo.save(fee);
    }

    // ── Calculation ───────────────────────────────────────────────────────────

    /**
     * Calculate (and persist) interchange for a specific authorization log entry.
     * Used as a dev/ops diagnostic and for settlement-time netting.
     */
    @Transactional
    public InterchangeResult calculate(UUID authorizationLogId) {
        return engine.calculateForAuth(authorizationLogId);
    }

    /**
     * Retrieve the most recent interchange calculation for an auth log entry.
     * Returns {@code null} if no calculation has been run yet.
     */
    @Transactional(readOnly = true)
    public InterchangeLog getLogForAuth(UUID authorizationLogId) {
        return logRepo.findTopByAuthorizationLogIdOrderByCalculatedAtDesc(authorizationLogId)
                .orElse(null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyRateRequest(InterchangeRate target, InterchangeRateRequest req) {
        target.setScheme(req.scheme().toUpperCase());
        target.setCardType(req.cardType());
        target.setMccCategory(req.mccCategory());
        target.setTransactionType(req.transactionType());
        target.setChannel(req.channel());
        target.setRatePercent(req.ratePercent());
        target.setFixedFee(req.fixedFee());
        target.setCurrencyCode(req.currencyCode().toUpperCase());
        target.setEffectiveFrom(req.effectiveFrom());
        target.setEffectiveTo(req.effectiveTo());
        target.setActive(req.active());
    }

    private void applyFeeRequest(SchemeFee target, SchemeFeeRequest req) {
        target.setScheme(req.scheme().toUpperCase());
        target.setFeeType(req.feeType());
        target.setRatePercent(req.ratePercent());
        target.setFixedFee(req.fixedFee());
        target.setEffectiveFrom(req.effectiveFrom());
        target.setEffectiveTo(req.effectiveTo());
        target.setActive(req.active());
    }
}
