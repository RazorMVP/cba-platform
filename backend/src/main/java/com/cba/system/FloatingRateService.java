package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FloatingRateService {

    public record CreateFloatingRateRequest(
        String name,
        boolean baseLendingRate,
        boolean active,
        List<PeriodRequest> ratePeriods
    ) {}

    public record PeriodRequest(
        LocalDate fromDate,
        BigDecimal interestRate,
        boolean differentialToBaseLendingRate
    ) {}

    private final FloatingRateRepository floatingRateRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<FloatingRate> listRates(boolean activeOnly, Pageable pageable) {
        return activeOnly
            ? floatingRateRepository.findByActiveTrue(pageable)
            : floatingRateRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public FloatingRate getRate(UUID id) {
        return floatingRateRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("FloatingRate", id));
    }

    @Transactional
    public FloatingRate createRate(CreateFloatingRateRequest req) {
        if (floatingRateRepository.existsByName(req.name())) {
            throw CbaException.conflict("FLOATING_RATE_NAME_EXISTS",
                "Floating rate '" + req.name() + "' already exists");
        }
        FloatingRate rate = new FloatingRate();
        rate.setName(req.name());
        rate.setBaseLendingRate(req.baseLendingRate());
        rate.setActive(req.active());

        if (req.ratePeriods() != null) {
            for (PeriodRequest pr : req.ratePeriods()) {
                FloatingRatePeriod period = new FloatingRatePeriod();
                period.setFloatingRate(rate);
                period.setFromDate(pr.fromDate());
                period.setInterestRate(pr.interestRate());
                period.setDifferentialToBaseLendingRate(pr.differentialToBaseLendingRate());
                rate.getRatePeriods().add(period);
            }
        }

        FloatingRate saved = floatingRateRepository.save(rate);
        auditLogService.log("FloatingRate", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public FloatingRate updateRate(UUID id, CreateFloatingRateRequest req) {
        FloatingRate rate = getRate(id);
        if (!rate.getName().equals(req.name()) && floatingRateRepository.existsByName(req.name())) {
            throw CbaException.conflict("FLOATING_RATE_NAME_EXISTS",
                "Floating rate '" + req.name() + "' already exists");
        }
        rate.setName(req.name());
        rate.setBaseLendingRate(req.baseLendingRate());
        rate.setActive(req.active());

        rate.getRatePeriods().clear();
        if (req.ratePeriods() != null) {
            for (PeriodRequest pr : req.ratePeriods()) {
                FloatingRatePeriod period = new FloatingRatePeriod();
                period.setFloatingRate(rate);
                period.setFromDate(pr.fromDate());
                period.setInterestRate(pr.interestRate());
                period.setDifferentialToBaseLendingRate(pr.differentialToBaseLendingRate());
                rate.getRatePeriods().add(period);
            }
        }

        FloatingRate saved = floatingRateRepository.save(rate);
        auditLogService.log("FloatingRate", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteRate(UUID id) {
        FloatingRate rate = getRate(id);
        floatingRateRepository.delete(rate);
        auditLogService.log("FloatingRate", id.toString(), "DELETE", null, null);
    }
}
