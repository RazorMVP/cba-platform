package com.cba.treasury;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LiquidityService {

    private final JdbcTemplate jdbc;
    private final LiquidityReserveRequirementRepository reserveRepo;
    private final LiquiditySnapshotRepository snapshotRepo;

    // ── DTOs (records) ──────────────────────────────────────────────────────────

    public record LiquidityPositionDto(
            String currency,
            BigDecimal cashOnHand,
            BigDecimal placementsDeployed,
            BigDecimal interbankLending,
            BigDecimal interbankBorrowing,
            BigDecimal netLiquidityPosition,
            BigDecimal reserveRequirement,
            BigDecimal surplusDeficit,
            String alertLevel,    // OK | WARN | BREACH
            LocalDate asOfDate
    ) {}

    public record CashFlowEntryDto(
            LocalDate date,
            String type,
            String reference,
            BigDecimal amount,
            String currency,
            String direction   // INFLOW | OUTFLOW
    ) {}

    // ── Live Position ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LiquidityPositionDto> getAllPositions() {
        List<String> currencies = getActiveCurrencies();
        return currencies.stream().map(this::computePosition).toList();
    }

    @Transactional(readOnly = true)
    public LiquidityPositionDto getPosition(String currency) {
        return computePosition(currency.toUpperCase());
    }

    private LiquidityPositionDto computePosition(String currency) {
        BigDecimal cashOnHand = queryDecimal(
                "SELECT COALESCE(SUM(balance), 0) FROM accounts WHERE status = 'ACTIVE' AND currency_code = ?",
                currency);

        BigDecimal deployed = queryDecimal(
                "SELECT COALESCE(SUM(principal_amount), 0) FROM treasury_placements WHERE status = 'ACTIVE' AND currency_code = ?",
                currency);

        BigDecimal lending = queryDecimal(
                "SELECT COALESCE(SUM(amount), 0) FROM treasury_interbank_positions WHERE status = 'ACTIVE' AND direction = 'LENDING' AND currency_code = ?",
                currency);

        BigDecimal borrowing = queryDecimal(
                "SELECT COALESCE(SUM(amount), 0) FROM treasury_interbank_positions WHERE status = 'ACTIVE' AND direction = 'BORROWING' AND currency_code = ?",
                currency);

        // Net = cash on hand + borrowing inflows − lending outflows − deployed capital
        BigDecimal net = cashOnHand.add(borrowing).subtract(lending).subtract(deployed);

        Optional<LiquidityReserveRequirement> req = reserveRepo.findByCurrencyCode(currency);
        BigDecimal requirement = req.map(LiquidityReserveRequirement::getMinimumBalance).orElse(BigDecimal.ZERO);
        BigDecimal surplus = net.subtract(requirement);

        String alertLevel = "OK";
        if (req.isPresent() && req.get().getAlertThresholdPercent() != null) {
            BigDecimal alertFloor = requirement.multiply(
                    req.get().getAlertThresholdPercent().divide(BigDecimal.valueOf(100)));
            if (surplus.compareTo(BigDecimal.ZERO) < 0) {
                alertLevel = "BREACH";
            } else if (net.subtract(requirement).compareTo(alertFloor) < 0) {
                alertLevel = "WARN";
            }
        }

        return new LiquidityPositionDto(currency, cashOnHand, deployed, lending, borrowing,
                net, requirement, surplus, alertLevel, LocalDate.now());
    }

    // ── Cash Flow Forecast ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CashFlowEntryDto> getCashFlowForecast(String currency, int days) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);
        List<CashFlowEntryDto> entries = new ArrayList<>();

        // Maturing placements (principal + expected return = inflow)
        List<Map<String, Object>> maturingPlacements = jdbc.queryForList(
                "SELECT reference, maturity_date, " +
                "COALESCE(expected_return, 0) + principal_amount AS amount " +
                "FROM treasury_placements " +
                "WHERE status = 'ACTIVE' AND currency_code = ? " +
                "AND maturity_date BETWEEN ? AND ? " +
                "ORDER BY maturity_date",
                currency, today, horizon);

        for (var row : maturingPlacements) {
            entries.add(new CashFlowEntryDto(
                    toLocalDate(row.get("maturity_date")),
                    "PLACEMENT_MATURITY",
                    (String) row.get("reference"),
                    toBigDecimal(row.get("amount")),
                    currency,
                    "INFLOW"));
        }

        // Settling interbank lending (inflow when counterparty repays)
        List<Map<String, Object>> interbankLending = jdbc.queryForList(
                "SELECT reference, maturity_date, amount " +
                "FROM treasury_interbank_positions " +
                "WHERE status = 'ACTIVE' AND direction = 'LENDING' AND currency_code = ? " +
                "AND maturity_date BETWEEN ? AND ? " +
                "ORDER BY maturity_date",
                currency, today, horizon);

        for (var row : interbankLending) {
            entries.add(new CashFlowEntryDto(
                    toLocalDate(row.get("maturity_date")),
                    "INTERBANK_REPAYMENT",
                    (String) row.get("reference"),
                    toBigDecimal(row.get("amount")),
                    currency,
                    "INFLOW"));
        }

        // Settling interbank borrowing (outflow when we repay)
        List<Map<String, Object>> interbankBorrowing = jdbc.queryForList(
                "SELECT reference, maturity_date, amount " +
                "FROM treasury_interbank_positions " +
                "WHERE status = 'ACTIVE' AND direction = 'BORROWING' AND currency_code = ? " +
                "AND maturity_date BETWEEN ? AND ? " +
                "ORDER BY maturity_date",
                currency, today, horizon);

        for (var row : interbankBorrowing) {
            entries.add(new CashFlowEntryDto(
                    toLocalDate(row.get("maturity_date")),
                    "INTERBANK_REPAYMENT",
                    (String) row.get("reference"),
                    toBigDecimal(row.get("amount")),
                    currency,
                    "OUTFLOW"));
        }

        // Loan repayments due (inflows to the bank)
        List<Map<String, Object>> loanRepayments = jdbc.queryForList(
                "SELECT lrs.due_date, SUM(lrs.total_due) AS amount " +
                "FROM loan_repayment_schedule lrs " +
                "JOIN loans l ON l.id = lrs.loan_id " +
                "WHERE lrs.status = 'PENDING' AND l.status IN ('ACTIVE','DISBURSED') " +
                "AND lrs.due_date BETWEEN ? AND ? " +
                "GROUP BY lrs.due_date ORDER BY lrs.due_date",
                today, horizon);

        for (var row : loanRepayments) {
            entries.add(new CashFlowEntryDto(
                    toLocalDate(row.get("due_date")),
                    "LOAN_REPAYMENT",
                    "Scheduled repayments",
                    toBigDecimal(row.get("amount")),
                    currency,
                    "INFLOW"));
        }

        entries.sort(Comparator.comparing(CashFlowEntryDto::date));
        return entries;
    }

    // ── Reserve Requirements CRUD ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LiquidityReserveRequirement> listReserves() {
        return reserveRepo.findAll();
    }

    @Transactional
    public LiquidityReserveRequirement createReserve(LiquidityReserveRequest req) {
        if (reserveRepo.findByCurrencyCode(req.currencyCode().toUpperCase()).isPresent()) {
            throw CbaException.conflict("RESERVE_EXISTS",
                    "Reserve requirement for " + req.currencyCode() + " already exists");
        }
        LiquidityReserveRequirement r = new LiquidityReserveRequirement();
        applyReserveRequest(r, req);
        return reserveRepo.save(r);
    }

    @Transactional
    public LiquidityReserveRequirement updateReserve(UUID id, LiquidityReserveRequest req) {
        LiquidityReserveRequirement r = reserveRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("LiquidityReserveRequirement", id));
        applyReserveRequest(r, req);
        return reserveRepo.save(r);
    }

    @Transactional
    public void deleteReserve(UUID id) {
        LiquidityReserveRequirement r = reserveRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("LiquidityReserveRequirement", id));
        r.setActive(false);
        reserveRepo.save(r);
    }

    private void applyReserveRequest(LiquidityReserveRequirement r, LiquidityReserveRequest req) {
        r.setCurrencyCode(req.currencyCode().toUpperCase());
        r.setMinimumBalance(req.minimumBalance());
        r.setMinimumRatioPercent(req.minimumRatioPercent());
        r.setAlertThresholdPercent(req.alertThresholdPercent());
        r.setRegulatoryReference(req.regulatoryReference());
    }

    // ── Snapshot History ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LiquiditySnapshot> getSnapshots(String currency, int limit) {
        List<LiquiditySnapshot> all =
                snapshotRepo.findByCurrencyCodeOrderBySnapshotDateDesc(currency.toUpperCase());
        return all.stream().limit(limit).toList();
    }

    /** Nightly CoB job — saves today's position for each active currency. */
    @Scheduled(cron = "0 50 23 * * *")
    @Transactional
    public void takeSnapshot() {
        LocalDate today = LocalDate.now();
        for (String currency : getActiveCurrencies()) {
            LiquidityPositionDto pos = computePosition(currency);
            LiquiditySnapshot snap = snapshotRepo
                    .findBySnapshotDateAndCurrencyCode(today, currency)
                    .orElse(new LiquiditySnapshot());
            snap.setSnapshotDate(today);
            snap.setCurrencyCode(currency);
            snap.setCashOnHand(pos.cashOnHand());
            snap.setPlacementsDeployed(pos.placementsDeployed());
            snap.setInterbankLending(pos.interbankLending());
            snap.setInterbankBorrowing(pos.interbankBorrowing());
            snap.setNetLiquidityPosition(pos.netLiquidityPosition());
            snap.setReserveRequirement(pos.reserveRequirement());
            snap.setSurplusDeficit(pos.surplusDeficit());
            snapshotRepo.save(snap);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private List<String> getActiveCurrencies() {
        List<String> currencies = new ArrayList<>();
        currencies.addAll(jdbc.queryForList(
                "SELECT DISTINCT currency_code FROM accounts WHERE status = 'ACTIVE'",
                String.class));
        currencies.addAll(jdbc.queryForList(
                "SELECT DISTINCT currency_code FROM treasury_placements WHERE status = 'ACTIVE'",
                String.class));
        currencies.addAll(jdbc.queryForList(
                "SELECT DISTINCT currency_code FROM treasury_interbank_positions WHERE status = 'ACTIVE'",
                String.class));
        return currencies.stream().distinct().sorted().toList();
    }

    private BigDecimal queryDecimal(String sql, Object... args) {
        BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class, args);
        return result != null ? result : BigDecimal.ZERO;
    }

    private LocalDate toLocalDate(Object val) {
        if (val instanceof java.sql.Date d) return d.toLocalDate();
        if (val instanceof LocalDate ld) return ld;
        return LocalDate.parse(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
