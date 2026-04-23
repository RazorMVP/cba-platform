package com.cba.treasury;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreasuryService {

    private final TreasuryPlacementRepository placementRepo;
    private final TreasuryInterbankPositionRepository interbankRepo;

    // ── Placements ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TreasuryPlacement> listPlacements() {
        return placementRepo.findAll();
    }

    @Transactional(readOnly = true)
    public TreasuryPlacement getPlacement(UUID id) {
        return placementRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("TreasuryPlacement", id));
    }

    @Transactional
    public TreasuryPlacement createPlacement(TreasuryPlacementRequest req) {
        if (placementRepo.findByReference(req.reference()).isPresent()) {
            throw CbaException.conflict("DUPLICATE_REFERENCE", "Reference " + req.reference() + " already exists");
        }
        var p = new TreasuryPlacement();
        applyPlacementRequest(p, req);
        return placementRepo.save(p);
    }

    @Transactional
    public TreasuryPlacement updatePlacement(UUID id, TreasuryPlacementRequest req) {
        var p = getPlacement(id);
        applyPlacementRequest(p, req);
        return placementRepo.save(p);
    }

    @Transactional
    public TreasuryPlacement commandPlacement(UUID id, String command) {
        var p = getPlacement(id);
        switch (command.toLowerCase(Locale.ROOT)) {
            case "activate" -> {
                if (p.getStatus() != TreasuryPlacement.Status.PENDING)
                    throw CbaException.badRequest("INVALID_STATE", "Only PENDING placements can be activated");
                p.setStatus(TreasuryPlacement.Status.ACTIVE);
            }
            case "mature" -> {
                if (p.getStatus() != TreasuryPlacement.Status.ACTIVE)
                    throw CbaException.badRequest("INVALID_STATE", "Only ACTIVE placements can be matured");
                p.setStatus(TreasuryPlacement.Status.MATURED);
            }
            case "cancel" -> {
                if (p.getStatus() == TreasuryPlacement.Status.MATURED)
                    throw CbaException.badRequest("INVALID_STATE", "Matured placements cannot be cancelled");
                p.setStatus(TreasuryPlacement.Status.CANCELLED);
            }
            default -> throw CbaException.badRequest("UNKNOWN_COMMAND", "Unknown command: " + command);
        }
        return placementRepo.save(p);
    }

    @Transactional
    public void deletePlacement(UUID id) {
        var p = getPlacement(id);
        if (p.getStatus() == TreasuryPlacement.Status.ACTIVE)
            throw CbaException.badRequest("INVALID_STATE", "Cannot delete an ACTIVE placement");
        placementRepo.delete(p);
    }

    // ── Interbank Positions ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TreasuryInterbankPosition> listPositions() {
        return interbankRepo.findAll();
    }

    @Transactional(readOnly = true)
    public TreasuryInterbankPosition getPosition(UUID id) {
        return interbankRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("TreasuryInterbankPosition", id));
    }

    @Transactional
    public TreasuryInterbankPosition createPosition(TreasuryInterbankRequest req) {
        if (interbankRepo.findByReference(req.reference()).isPresent()) {
            throw CbaException.conflict("DUPLICATE_REFERENCE", "Reference " + req.reference() + " already exists");
        }
        var pos = new TreasuryInterbankPosition();
        applyInterbankRequest(pos, req);
        return interbankRepo.save(pos);
    }

    @Transactional
    public TreasuryInterbankPosition updatePosition(UUID id, TreasuryInterbankRequest req) {
        var pos = getPosition(id);
        applyInterbankRequest(pos, req);
        return interbankRepo.save(pos);
    }

    @Transactional
    public TreasuryInterbankPosition commandPosition(UUID id, String command) {
        var pos = getPosition(id);
        switch (command.toLowerCase(Locale.ROOT)) {
            case "settle" -> {
                if (pos.getStatus() != TreasuryInterbankPosition.Status.ACTIVE)
                    throw CbaException.badRequest("INVALID_STATE", "Only ACTIVE positions can be settled");
                pos.setStatus(TreasuryInterbankPosition.Status.SETTLED);
            }
            case "cancel" -> {
                if (pos.getStatus() != TreasuryInterbankPosition.Status.ACTIVE)
                    throw CbaException.badRequest("INVALID_STATE", "Only ACTIVE positions can be cancelled");
                pos.setStatus(TreasuryInterbankPosition.Status.CANCELLED);
            }
            default -> throw CbaException.badRequest("UNKNOWN_COMMAND", "Unknown command: " + command);
        }
        return interbankRepo.save(pos);
    }

    @Transactional
    public void deletePosition(UUID id) {
        var pos = getPosition(id);
        if (pos.getStatus() == TreasuryInterbankPosition.Status.ACTIVE)
            throw CbaException.badRequest("INVALID_STATE", "Cannot delete an ACTIVE position");
        interbankRepo.delete(pos);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void applyPlacementRequest(TreasuryPlacement p, TreasuryPlacementRequest req) {
        p.setReference(req.reference());
        p.setCounterpartyName(req.counterpartyName());
        p.setCounterpartyBic(req.counterpartyBic());
        p.setPlacementType(TreasuryPlacement.PlacementType.valueOf(req.placementType()));
        p.setPrincipal(req.principal());
        p.setInterestRate(req.interestRate());
        p.setCurrencyCode(req.currencyCode() != null ? req.currencyCode() : "USD");
        p.setStartDate(req.startDate());
        p.setMaturityDate(req.maturityDate());
        p.setExpectedReturn(req.expectedReturn());
        p.setGlSourceAccount(req.glSourceAccount());
        p.setGlIncomeAccount(req.glIncomeAccount());
        p.setNotes(req.notes());
    }

    private void applyInterbankRequest(TreasuryInterbankPosition pos, TreasuryInterbankRequest req) {
        pos.setReference(req.reference());
        pos.setCounterpartyName(req.counterpartyName());
        pos.setCounterpartyBic(req.counterpartyBic());
        pos.setDirection(TreasuryInterbankPosition.Direction.valueOf(req.direction()));
        pos.setAmount(req.amount());
        pos.setCurrencyCode(req.currencyCode() != null ? req.currencyCode() : "USD");
        pos.setInterestRate(req.interestRate());
        pos.setStartDate(req.startDate());
        pos.setMaturityDate(req.maturityDate());
        pos.setSettlementGl(req.settlementGl());
        pos.setNotes(req.notes());
    }
}
