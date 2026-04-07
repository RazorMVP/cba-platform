package com.cba.group;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.group.dto.CollectionSheetRequest;
import com.cba.group.dto.GroupRequest;
import com.cba.group.dto.GroupResponse;
import com.cba.loan.LoanRepository;
import com.cba.loan.LoanStatus;
import com.cba.office.OfficeRepository;
import com.cba.office.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final CenterRepository centerRepository;
    private final GroupMemberRepository memberRepository;
    private final CollectionSheetRepository sheetRepository;
    private final GlimAccountRepository glimRepository;
    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final LoanRepository loanRepository;
    private final AuditLogService auditLogService;

    // ── Groups ───────────────────────────────────────────────────────────────

    @Transactional
    public GroupResponse createGroup(GroupRequest req) {
        Group group = new Group();
        group.setName(req.name());
        group.setExternalId(req.externalId());
        group.setActivationDate(req.activationDate());
        group.setStatus(req.activationDate() != null && !req.activationDate().isAfter(LocalDate.now())
                ? Group.Status.ACTIVE : Group.Status.PENDING);

        group.setOffice(officeRepository.findById(req.officeId())
                .orElseThrow(() -> CbaException.notFound("Office", req.officeId().toString())));

        if (req.staffId() != null) {
            group.setStaff(staffRepository.findById(req.staffId())
                    .orElseThrow(() -> CbaException.notFound("Staff", req.staffId().toString())));
        }
        if (req.centerId() != null) {
            group.setCenter(centerRepository.findById(req.centerId())
                    .orElseThrow(() -> CbaException.notFound("Center", req.centerId().toString())));
        }

        Group saved = groupRepository.save(group);
        auditLogService.log("GROUP", saved.getId().toString(), "CREATED", null, "name=" + saved.getName());
        return GroupResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups(UUID officeId) {
        List<Group> groups = officeId != null
                ? groupRepository.findByOfficeId(officeId)
                : groupRepository.findAll();
        return groups.stream().map(GroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID id) {
        return GroupResponse.from(findGroupOrThrow(id));
    }

    @Transactional
    public GroupResponse activateGroup(UUID id) {
        Group group = findGroupOrThrow(id);
        group.setStatus(Group.Status.ACTIVE);
        group.setActivationDate(LocalDate.now());
        Group saved = groupRepository.save(group);
        auditLogService.log("GROUP", id.toString(), "ACTIVATED", null, null);
        return GroupResponse.from(saved);
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @Transactional
    public void addMember(UUID groupId, UUID customerId) {
        Group group = findGroupOrThrow(groupId);
        if (memberRepository.existsByGroupIdAndCustomerId(groupId, customerId)) {
            throw CbaException.badRequest("MEMBER_EXISTS", "Customer is already a member of this group");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> CbaException.notFound("Customer", customerId.toString()));

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setCustomer(customer);
        member.setJoiningDate(LocalDate.now());
        member.setActive(true);
        memberRepository.save(member);
        auditLogService.log("GROUP_MEMBER", groupId.toString(), "ADDED", null, "customerId=" + customerId);
    }

    @Transactional
    public void removeMember(UUID groupId, UUID customerId) {
        List<GroupMember> members = memberRepository.findByGroupIdAndActiveTrue(groupId);
        GroupMember member = members.stream()
                .filter(m -> m.getCustomer().getId().equals(customerId))
                .findFirst()
                .orElseThrow(() -> CbaException.notFound("GroupMember", customerId.toString()));
        member.setActive(false);
        memberRepository.save(member);
    }

    // ── Collection Sheets ─────────────────────────────────────────────────────

    @Transactional
    public CollectionSheet generateCollectionSheet(CollectionSheetRequest req) {
        Group group = findGroupOrThrow(req.groupId());

        if (sheetRepository.findByGroupIdAndMeetingDate(req.groupId(), req.meetingDate()).isPresent()) {
            throw CbaException.badRequest("SHEET_EXISTS",
                    "A collection sheet already exists for this group on " + req.meetingDate());
        }

        CollectionSheet sheet = new CollectionSheet();
        sheet.setGroup(group);
        sheet.setMeetingDate(req.meetingDate());

        // Pre-populate items from active members' due installments
        List<GroupMember> members = memberRepository.findByGroupIdAndActiveTrue(req.groupId());
        for (GroupMember m : members) {
            // Find the member's active loans and their next due installment
            loanRepository.findByCustomerId(m.getCustomer().getId(), PageRequest.of(0, 10))
                    .getContent().stream()
                    .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.IN_ARREARS)
                    .forEach(loan -> {
                        CollectionSheetItem item = new CollectionSheetItem();
                        item.setCollectionSheet(sheet);
                        item.setCustomer(m.getCustomer());
                        item.setLoanId(loan.getId());
                        // Use outstanding balance as due amount if no installment schedule available
                        item.setDueAmount(loan.getOutstandingBalance());
                        item.setCollected(false);
                        sheet.getItems().add(item);
                    });
        }

        CollectionSheet saved = sheetRepository.save(sheet);
        auditLogService.log("COLLECTION_SHEET", saved.getId().toString(), "GENERATED", null,
                "groupId=" + req.groupId() + ",meetingDate=" + req.meetingDate());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CollectionSheet> getGroupSheets(UUID groupId) {
        return sheetRepository.findByGroupId(groupId);
    }

    // ── GLIM ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GlimAccount> getGlimAccounts(UUID groupId) {
        return glimRepository.findByGroupId(groupId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Group findGroupOrThrow(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Group", id.toString()));
    }
}
