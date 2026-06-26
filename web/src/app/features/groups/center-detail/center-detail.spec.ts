import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CenterDetailComponent } from './center-detail';
import { GroupsService, Center, Group, GroupMember } from '../groups.service';

type Svc = Record<
  'getCenter' | 'getCenterGroups' | 'getCenterMembers' | 'activateCenter',
  ReturnType<typeof vi.fn>
>;

function center(over: Partial<Center> = {}): Center {
  return {
    id: 'ct1', name: 'North', externalId: null, officeId: 'o1', officeName: 'HQ',
    staffId: null, staffName: null, status: 'PENDING', activationDate: null,
    groupCount: 2, ...over,
  };
}
const grp: Group = {
  id: 'g1', name: 'Alpha', externalId: null, centerId: 'ct1', centerName: 'North',
  officeId: 'o1', officeName: 'HQ', staffId: null, staffName: null,
  status: 'ACTIVE', activationDate: '2026-01-01', memberCount: 4,
};
const mem: GroupMember = { customerId: 'c1', customerName: 'Jo', accountNo: 'A1', joinedDate: '2026-01-01' };

describe('CenterDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getCenter: vi.fn().mockReturnValue(of(center())),
      getCenterGroups: vi.fn().mockReturnValue(of([grp])),
      getCenterMembers: vi.fn().mockReturnValue(of([mem])),
      activateCenter: vi.fn().mockReturnValue(of(center({ status: 'ACTIVE' }))),
    };
    TestBed.configureTestingModule({
      imports: [CenterDetailComponent],
      providers: [
        provideRouter([]),
        { provide: GroupsService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ct1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CenterDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the center + its groups from the route id on init', () => {
    const c = make();
    expect(svc.getCenter).toHaveBeenCalledWith('ct1');
    expect(svc.getCenterGroups).toHaveBeenCalledWith('ct1');
    expect(c.center?.id).toBe('ct1');
    expect(c.groups).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.getCenter.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load center.');
    expect(c.loading).toBe(false);
  });

  describe('switchTab', () => {
    it('lazy-loads members only once on first members tab visit', () => {
      const c = make();
      c.switchTab('members');
      expect(svc.getCenterMembers).toHaveBeenCalledWith('ct1');
      expect(c.members).toHaveLength(1);
      c.switchTab('groups');
      c.switchTab('members');
      expect(svc.getCenterMembers).toHaveBeenCalledTimes(1);
    });
  });

  it('activateCenter replaces the loaded center', () => {
    const c = make();
    c.activateCenter();
    expect(svc.activateCenter).toHaveBeenCalledWith('ct1');
    expect(c.center?.status).toBe('ACTIVE');
  });

  it('statusVariant maps status to a badge variant', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('PENDING')).toBe('warning');
    expect(c.statusVariant('CLOSED')).toBe('neutral');
  });
});
