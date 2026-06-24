import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { GroupsService } from './groups.service';

describe('GroupsService', () => {
  let service: GroupsService;
  let api: Record<'get' | 'post' | 'put' | 'delete' | 'command', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
      command: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [GroupsService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(GroupsService);
  });

  it('listGroups passes a status filter only when given', () => {
    service.listGroups('ACTIVE').subscribe();
    expect(api.get).toHaveBeenCalledWith('/groups', { status: 'ACTIVE' });
    service.listGroups().subscribe();
    expect(api.get).toHaveBeenCalledWith('/groups', undefined);
  });

  it('group CRUD + activate route correctly', () => {
    service.getGroup('g1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/groups/g1');
    const req = { name: 'G', officeId: 'o1' };
    service.createGroup(req).subscribe();
    expect(api.post).toHaveBeenCalledWith('/groups', req);
    service.updateGroup('g1', req).subscribe();
    expect(api.put).toHaveBeenCalledWith('/groups/g1', req);
    service.activateGroup('g1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/groups/g1', 'activate');
  });

  it('member add/remove route under the group', () => {
    service.addMember('g1', 'c1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/groups/g1/members/c1', {});
    service.removeMember('g1', 'c1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/groups/g1/members/c1');
  });

  it('generateCollectionSheet posts group + meetingDate', () => {
    service.generateCollectionSheet('g1', '2026-06-01').subscribe();
    expect(api.post).toHaveBeenCalledWith('/collectionsheets', {
      groupId: 'g1',
      meetingDate: '2026-06-01',
    });
  });

  it('assignStaff embeds staffId in the URL; unassignStaff deletes', () => {
    service.assignStaff('g1', 's9').subscribe();
    expect(api.post).toHaveBeenCalledWith('/groups/g1/assignstaff?staffId=s9', {});
    service.unassignStaff('g1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/groups/g1/assignstaff');
  });

  it('center operations route correctly', () => {
    service.listCenters('PENDING').subscribe();
    expect(api.get).toHaveBeenCalledWith('/centers', { status: 'PENDING' });
    service.activateCenter('ct1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/centers/ct1', 'activate');
    service.getCenterGroups('ct1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/centers/ct1/groups');
  });
});
