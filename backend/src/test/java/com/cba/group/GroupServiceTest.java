package com.cba.group;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.group.dto.CollectionSheetRequest;
import com.cba.group.dto.GroupRequest;
import com.cba.group.dto.GroupResponse;
import com.cba.loan.LoanRepository;
import com.cba.office.Office;
import com.cba.office.OfficeRepository;
import com.cba.office.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService — unit tests")
class GroupServiceTest {

    @Mock GroupRepository groupRepository;
    @Mock CenterRepository centerRepository;
    @Mock GroupMemberRepository memberRepository;
    @Mock CollectionSheetRepository sheetRepository;
    @Mock GlimAccountRepository glimRepository;
    @Mock OfficeRepository officeRepository;
    @Mock StaffRepository staffRepository;
    @Mock CustomerRepository customerRepository;
    @Mock LoanRepository loanRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks GroupService groupService;

    private UUID groupId;
    private UUID officeId;
    private Office office;
    private Group group;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        officeId = UUID.randomUUID();

        office = new Office();
        office.setId(officeId);
        office.setName("Head Office");

        group = new Group();
        group.setId(groupId);
        group.setName("Savings Circle A");
        group.setOffice(office);
        group.setStatus(Group.Status.PENDING);
    }

    @Nested
    @DisplayName("Group CRUD")
    class GroupCrud {

        @Test
        @DisplayName("createGroup with active date sets ACTIVE status")
        void createGroup_activeDate_setsActive() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));
            when(groupRepository.save(any())).thenAnswer(inv -> {
                Group g = inv.getArgument(0);
                g.setId(groupId);
                return g;
            });

            GroupRequest req = new GroupRequest("Savings Circle A", null, officeId, null, null, LocalDate.now());
            GroupResponse result = groupService.createGroup(req);
            assertThat(result.name()).isEqualTo("Savings Circle A");
            assertThat(result.status()).isEqualTo(Group.Status.ACTIVE);
        }

        @Test
        @DisplayName("createGroup with no activation date sets PENDING status")
        void createGroup_noDate_setsPending() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));
            when(groupRepository.save(any())).thenAnswer(inv -> {
                Group g = inv.getArgument(0);
                g.setId(groupId);
                return g;
            });

            GroupRequest req = new GroupRequest("Pending Group", null, officeId, null, null, null);
            GroupResponse result = groupService.createGroup(req);
            assertThat(result.status()).isEqualTo(Group.Status.PENDING);
        }

        @Test
        @DisplayName("createGroup throws when office not found")
        void createGroup_officeNotFound_throws() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.empty());

            GroupRequest req = new GroupRequest("Group A", null, officeId, null, null, null);
            assertThatThrownBy(() -> groupService.createGroup(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("listGroups returns all groups when no officeId filter")
        void listGroups_noFilter_returnsAll() {
            when(groupRepository.findAll()).thenReturn(List.of(group));
            List<GroupResponse> result = groupService.listGroups(null);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("listGroups filters by officeId when provided")
        void listGroups_withOfficeId() {
            when(groupRepository.findByOfficeId(officeId)).thenReturn(List.of(group));
            List<GroupResponse> result = groupService.listGroups(officeId);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getGroup returns group when found")
        void getGroup_found() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            GroupResponse result = groupService.getGroup(groupId);
            assertThat(result.name()).isEqualTo("Savings Circle A");
        }

        @Test
        @DisplayName("getGroup throws when not found")
        void getGroup_notFound_throws() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> groupService.getGroup(groupId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("activateGroup sets ACTIVE status")
        void activateGroup_success() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GroupResponse result = groupService.activateGroup(groupId);
            assertThat(result.status()).isEqualTo(Group.Status.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Member Management")
    class MemberManagement {

        @Test
        @DisplayName("addMember adds customer to group")
        void addMember_success() {
            UUID customerId = UUID.randomUUID();
            Customer customer = new Customer();
            customer.setId(customerId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(memberRepository.existsByGroupIdAndCustomerId(groupId, customerId)).thenReturn(false);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

            assertThatCode(() -> groupService.addMember(groupId, customerId)).doesNotThrowAnyException();
            verify(memberRepository).save(any(GroupMember.class));
        }

        @Test
        @DisplayName("addMember throws when customer already in group")
        void addMember_duplicate_throws() {
            UUID customerId = UUID.randomUUID();
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(memberRepository.existsByGroupIdAndCustomerId(groupId, customerId)).thenReturn(true);

            assertThatThrownBy(() -> groupService.addMember(groupId, customerId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already a member");
        }

        @Test
        @DisplayName("removeMember deactivates member")
        void removeMember_success() {
            UUID customerId = UUID.randomUUID();
            Customer customer = new Customer();
            customer.setId(customerId);

            GroupMember member = new GroupMember();
            member.setCustomer(customer);
            member.setActive(true);

            when(memberRepository.findByGroupIdAndActiveTrue(groupId)).thenReturn(List.of(member));

            assertThatCode(() -> groupService.removeMember(groupId, customerId)).doesNotThrowAnyException();
            verify(memberRepository).save(argThat(m -> !m.isActive()));
        }

        @Test
        @DisplayName("removeMember throws when member not found")
        void removeMember_notFound_throws() {
            UUID customerId = UUID.randomUUID();
            when(memberRepository.findByGroupIdAndActiveTrue(groupId)).thenReturn(List.of());

            assertThatThrownBy(() -> groupService.removeMember(groupId, customerId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Collection Sheets")
    class CollectionSheets {

        @Test
        @DisplayName("generateCollectionSheet creates sheet for group with no active members")
        void generateCollectionSheet_emptyMembers() {
            LocalDate meetingDate = LocalDate.now().plusDays(7);
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(sheetRepository.findByGroupIdAndMeetingDate(groupId, meetingDate))
                .thenReturn(Optional.empty());
            when(memberRepository.findByGroupIdAndActiveTrue(groupId)).thenReturn(List.of());
            when(sheetRepository.save(any())).thenAnswer(inv -> {
                CollectionSheet s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

            CollectionSheetRequest req = new CollectionSheetRequest(groupId, meetingDate);
            CollectionSheet result = groupService.generateCollectionSheet(req);
            assertThat(result.getMeetingDate()).isEqualTo(meetingDate);
        }

        @Test
        @DisplayName("generateCollectionSheet throws when sheet already exists for date")
        void generateCollectionSheet_duplicate_throws() {
            LocalDate meetingDate = LocalDate.now().plusDays(7);
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(sheetRepository.findByGroupIdAndMeetingDate(groupId, meetingDate))
                .thenReturn(Optional.of(new CollectionSheet()));

            CollectionSheetRequest req = new CollectionSheetRequest(groupId, meetingDate);
            assertThatThrownBy(() -> groupService.generateCollectionSheet(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("getGroupSheets returns list of sheets")
        void getGroupSheets_returnsList() {
            when(sheetRepository.findByGroupId(groupId)).thenReturn(List.of(new CollectionSheet()));
            assertThat(groupService.getGroupSheets(groupId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("GLIM and Staff")
    class GlimAndStaff {

        @Test
        @DisplayName("getGlimAccounts returns GLIM accounts for group")
        void getGlimAccounts_returnsList() {
            when(glimRepository.findByGroupId(groupId)).thenReturn(List.of(new GlimAccount()));
            assertThat(groupService.getGlimAccounts(groupId)).hasSize(1);
        }

        @Test
        @DisplayName("unassignStaff sets staff to null")
        void unassignStaff_success() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GroupResponse result = groupService.unassignStaff(groupId);
            assertThat(result.staffId()).isNull();
        }
    }
}
