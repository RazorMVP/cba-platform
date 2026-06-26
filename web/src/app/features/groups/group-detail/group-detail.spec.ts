import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { GroupDetailComponent } from './group-detail';
import {
  GroupsService, Group, GroupMember, CollectionSheet, GlimAccount,
} from '../groups.service';

type Svc = Record<
  'getGroup' | 'getGroupMembers' | 'getGlimAccounts' | 'generateCollectionSheet' |
  'activateGroup' | 'addMember' | 'removeMember' | 'assignStaff' | 'unassignStaff',
  ReturnType<typeof vi.fn>
>;

function group(over: Partial<Group> = {}): Group {
  return {
    id: 'g1', name: 'Alpha', externalId: null, centerId: null, centerName: null,
    officeId: 'o1', officeName: 'HQ', staffId: null, staffName: null,
    status: 'PENDING', activationDate: null, memberCount: 0, ...over,
  };
}
function member(over: Partial<GroupMember> = {}): GroupMember {
  return { customerId: 'c1', customerName: 'Jo', accountNo: 'A1', joinedDate: '2026-01-01', ...over };
}
const sheet: CollectionSheet = {
  id: 's1', groupId: 'g1', meetingDate: '2026-06-01',
  items: [
    { customerId: 'c1', customerName: 'Jo', loanId: 'l1', loanAccountNo: 'LN1', dueAmount: 100, paidAmount: 40, outstanding: 60 },
    { customerId: 'c2', customerName: 'Al', loanId: 'l2', loanAccountNo: 'LN2', dueAmount: 50, paidAmount: 50, outstanding: 0 },
  ],
  totalDue: 150, totalCollected: 90,
};
const glim: GlimAccount = {
  id: 'gl1', groupId: 'g1', accountNo: 'GL1', totalAmount: 500, members: [], status: 'ACTIVE',
};

describe('GroupDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getGroup: vi.fn().mockReturnValue(of(group())),
      getGroupMembers: vi.fn().mockReturnValue(of([member()])),
      getGlimAccounts: vi.fn().mockReturnValue(of([glim])),
      generateCollectionSheet: vi.fn().mockReturnValue(of(sheet)),
      activateGroup: vi.fn().mockReturnValue(of(group({ status: 'ACTIVE' }))),
      addMember: vi.fn().mockReturnValue(of(void 0)),
      removeMember: vi.fn().mockReturnValue(of(void 0)),
      assignStaff: vi.fn().mockReturnValue(of(group({ staffId: 's9', staffName: 'Sam' }))),
      unassignStaff: vi.fn().mockReturnValue(of(group({ staffId: null }))),
    };
    TestBed.configureTestingModule({
      imports: [GroupDetailComponent],
      providers: [
        provideRouter([]),
        { provide: GroupsService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'g1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(GroupDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the group + members from the route id on init', () => {
    const c = make();
    expect(svc.getGroup).toHaveBeenCalledWith('g1');
    expect(svc.getGroupMembers).toHaveBeenCalledWith('g1');
    expect(c.group?.id).toBe('g1');
    expect(c.members).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.getGroup.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load group.');
    expect(c.loading).toBe(false);
  });

  describe('switchTab', () => {
    it('lazy-loads GLIM accounts only once on first glim tab visit', () => {
      const c = make();
      c.switchTab('glim');
      expect(svc.getGlimAccounts).toHaveBeenCalledWith('g1');
      expect(c.glim).toHaveLength(1);
      c.switchTab('members');
      c.switchTab('glim');
      expect(svc.getGlimAccounts).toHaveBeenCalledTimes(1);
    });
  });

  describe('generateSheet', () => {
    it('is a no-op without a date', () => {
      const c = make();
      c.sheetDate = '';
      c.generateSheet();
      expect(svc.generateCollectionSheet).not.toHaveBeenCalled();
    });

    it('generates and stores the sheet', () => {
      const c = make();
      c.sheetDate = '2026-06-01';
      c.generateSheet();
      expect(svc.generateCollectionSheet).toHaveBeenCalledWith('g1', '2026-06-01');
      expect(c.sheet?.id).toBe('s1');
      expect(c.sheetLoading).toBe(false);
    });
  });

  it('totalDue / totalCollected sum the sheet items', () => {
    const c = make();
    expect(c.totalDue).toBe(0);
    c.sheet = sheet;
    expect(c.totalDue).toBe(150);
    expect(c.totalCollected).toBe(90);
  });

  it('activateGroup replaces the loaded group', () => {
    const c = make();
    c.activateGroup();
    expect(svc.activateGroup).toHaveBeenCalledWith('g1');
    expect(c.group?.status).toBe('ACTIVE');
  });

  describe('members', () => {
    it('submitAddMember is a no-op without a customer id', () => {
      const c = make();
      c.newCustomerId = '';
      c.submitAddMember();
      expect(svc.addMember).not.toHaveBeenCalled();
    });

    it('submitAddMember adds and reloads members', () => {
      const c = make();
      c.openAddMember();
      c.newCustomerId = 'c2';
      c.submitAddMember();
      expect(svc.addMember).toHaveBeenCalledWith('g1', 'c2');
      expect(svc.getGroupMembers).toHaveBeenCalledTimes(2);
      expect(c.addMemberModal).toBe(false);
    });

    it('submitAddMember surfaces an error on failure', () => {
      svc.addMember.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.newCustomerId = 'c2';
      c.submitAddMember();
      expect(c.memberError).toBe('Failed to add member.');
      expect(c.memberWorking).toBe(false);
    });

    it('removeMember drops the member from the list', () => {
      const c = make();
      c.members = [member({ customerId: 'c1' }), member({ customerId: 'c2' })];
      c.removeMember('c1');
      expect(svc.removeMember).toHaveBeenCalledWith('g1', 'c1');
      expect(c.members).toHaveLength(1);
      expect(c.members[0].customerId).toBe('c2');
    });
  });

  describe('staff', () => {
    it('submitAssignStaff assigns and updates the group', () => {
      const c = make();
      c.openStaffModal();
      c.newStaffId = 's9';
      c.submitAssignStaff();
      expect(svc.assignStaff).toHaveBeenCalledWith('g1', 's9');
      expect(c.group?.staffId).toBe('s9');
      expect(c.staffModal).toBe(false);
    });

    it('removeStaff is a no-op when no staff assigned', () => {
      const c = make();
      c.group = group({ staffId: null });
      c.removeStaff();
      expect(svc.unassignStaff).not.toHaveBeenCalled();
    });

    it('removeStaff unassigns when staff present', () => {
      const c = make();
      c.group = group({ staffId: 's9', staffName: 'Sam' });
      c.removeStaff();
      expect(svc.unassignStaff).toHaveBeenCalledWith('g1');
      expect(c.group?.staffId).toBeNull();
    });
  });
});
