package com.cba.group;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.group.dto.CenterRequest;
import com.cba.group.dto.CenterResponse;
import com.cba.office.OfficeRepository;
import com.cba.office.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CenterService {

    private final CenterRepository centerRepository;
    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public CenterResponse createCenter(CenterRequest req) {
        Center center = new Center();
        applyRequest(center, req);
        if (req.activationDate() != null && !req.activationDate().isAfter(LocalDate.now())) {
            center.setStatus(Center.Status.ACTIVE);
        } else {
            center.setStatus(Center.Status.INACTIVE);
        }
        Center saved = centerRepository.save(center);
        auditLogService.log("CENTER", saved.getId().toString(), "CREATE", null, saved);
        return CenterResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CenterResponse> listCenters(UUID officeId) {
        List<Center> centers = officeId != null
                ? centerRepository.findByOfficeId(officeId)
                : centerRepository.findAll();
        return centers.stream().map(CenterResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CenterResponse getCenter(UUID id) {
        return CenterResponse.from(findOrThrow(id));
    }

    @Transactional
    public CenterResponse updateCenter(UUID id, CenterRequest req) {
        Center center = findOrThrow(id);
        applyRequest(center, req);
        Center saved = centerRepository.save(center);
        auditLogService.log("CENTER", id.toString(), "UPDATE", null, saved);
        return CenterResponse.from(saved);
    }

    @Transactional
    public CenterResponse activateCenter(UUID id) {
        Center center = findOrThrow(id);
        center.setStatus(Center.Status.ACTIVE);
        center.setActivationDate(LocalDate.now());
        Center saved = centerRepository.save(center);
        auditLogService.log("CENTER", id.toString(), "ACTIVATE", null, null);
        return CenterResponse.from(saved);
    }

    @Transactional
    public void deleteCenter(UUID id) {
        Center center = findOrThrow(id);
        centerRepository.delete(center);
        auditLogService.log("CENTER", id.toString(), "DELETE", null, null);
    }

    private void applyRequest(Center center, CenterRequest req) {
        center.setName(req.name());
        center.setExternalId(req.externalId());
        center.setActivationDate(req.activationDate());
        center.setMeetingDayOfWeek(req.meetingDayOfWeek());
        center.setOffice(officeRepository.findById(req.officeId())
                .orElseThrow(() -> CbaException.notFound("Office", req.officeId().toString())));
        if (req.staffId() != null) {
            center.setStaff(staffRepository.findById(req.staffId())
                    .orElseThrow(() -> CbaException.notFound("Staff", req.staffId().toString())));
        } else {
            center.setStaff(null);
        }
    }

    private Center findOrThrow(UUID id) {
        return centerRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Center", id.toString()));
    }
}
