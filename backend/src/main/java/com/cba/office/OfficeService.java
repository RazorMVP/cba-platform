package com.cba.office;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.office.dto.OfficeRequest;
import com.cba.office.dto.OfficeResponse;
import com.cba.office.dto.StaffRequest;
import com.cba.office.dto.StaffResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfficeService {

    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;
    private final AuditLogService auditLogService;

    // ── Offices ──────────────────────────────────────────────────────────────

    @Transactional
    public OfficeResponse createOffice(OfficeRequest request) {
        Office parent = null;
        if (request.parentId() != null) {
            parent = officeRepository.findById(request.parentId())
                    .orElseThrow(() -> CbaException.notFound("Office", request.parentId().toString()));
        }

        Office office = new Office();
        office.setName(request.name());
        office.setExternalId(request.externalId());
        office.setOpeningDate(request.openingDate());
        office.setDescription(request.description());
        office.setParent(parent);
        office.setActive(true);

        // Temporary save to get the UUID for hierarchy computation
        Office saved = officeRepository.save(office);
        String hierarchy = parent != null
                ? parent.getHierarchy() + saved.getId() + "."
                : "." + saved.getId() + ".";
        saved.setHierarchy(hierarchy);
        saved = officeRepository.save(saved);

        auditLogService.log("OFFICE", saved.getId().toString(), "CREATED", null, "name=" + saved.getName());
        return OfficeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OfficeResponse> getAllOffices() {
        return officeRepository.findByActiveTrue().stream().map(OfficeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OfficeResponse getOffice(UUID id) {
        return OfficeResponse.from(findOfficeOrThrow(id));
    }

    @Transactional
    public OfficeResponse updateOffice(UUID id, OfficeRequest request) {
        Office office = findOfficeOrThrow(id);
        office.setName(request.name());
        office.setDescription(request.description());
        office.setOpeningDate(request.openingDate());
        Office saved = officeRepository.save(office);
        auditLogService.log("OFFICE", id.toString(), "UPDATED", null, "name=" + saved.getName());
        return OfficeResponse.from(saved);
    }

    // ── Staff ─────────────────────────────────────────────────────────────────

    @Transactional
    public StaffResponse createStaff(StaffRequest request) {
        Office office = findOfficeOrThrow(request.officeId());
        Staff staff = new Staff();
        staff.setFirstName(request.firstName());
        staff.setLastName(request.lastName());
        staff.setEmail(request.email());
        staff.setMobileNo(request.mobileNo());
        staff.setJoiningDate(request.joiningDate());
        staff.setLoanOfficer(request.loanOfficer());
        staff.setOffice(office);
        staff.setActive(true);
        Staff saved = staffRepository.save(staff);
        auditLogService.log("STAFF", saved.getId().toString(), "CREATED", null, "name=" + saved.getDisplayName());
        return StaffResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> getAllStaff(UUID officeId) {
        List<Staff> list = officeId != null
                ? staffRepository.findByOfficeIdAndActiveTrue(officeId)
                : staffRepository.findByActiveTrue();
        return list.stream().map(StaffResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(UUID id) {
        return StaffResponse.from(findStaffOrThrow(id));
    }

    @Transactional
    public StaffResponse updateStaff(UUID id, StaffRequest request) {
        Staff staff = findStaffOrThrow(id);
        staff.setFirstName(request.firstName());
        staff.setLastName(request.lastName());
        staff.setEmail(request.email());
        staff.setMobileNo(request.mobileNo());
        staff.setLoanOfficer(request.loanOfficer());
        Staff saved = staffRepository.save(staff);
        auditLogService.log("STAFF", id.toString(), "UPDATED", null, null);
        return StaffResponse.from(saved);
    }

    @Transactional
    public void deactivateStaff(UUID id) {
        Staff staff = findStaffOrThrow(id);
        staff.setActive(false);
        staffRepository.save(staff);
        auditLogService.log("STAFF", id.toString(), "DEACTIVATED", null, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Office findOfficeOrThrow(UUID id) {
        return officeRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Office", id.toString()));
    }

    private Staff findStaffOrThrow(UUID id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Staff", id.toString()));
    }
}
