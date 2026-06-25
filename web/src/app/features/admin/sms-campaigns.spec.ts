import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SmsCampaignsComponent } from './sms-campaigns';
import { AdminService, SmsCampaign } from './admin.service';

type Svc = Record<
  'listSmsCampaigns' | 'createSmsCampaign' | 'updateSmsCampaign' | 'deleteSmsCampaign' |
  'activateSmsCampaign' | 'listSmsMessages',
  ReturnType<typeof vi.fn>
>;

function campaign(over: Partial<SmsCampaign> = {}): SmsCampaign {
  return {
    id: 'sc1', campaignName: 'Promo', campaignType: 'ALL', triggerType: 'DIRECT',
    message: 'Hello', status: 'PENDING', ...over,
  };
}
function page(content: SmsCampaign[], totalElements: number) {
  return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
}

describe('SmsCampaignsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listSmsCampaigns: vi.fn().mockReturnValue(page([campaign()], 1)),
      createSmsCampaign: vi.fn().mockReturnValue(of(campaign({ id: 'sc2' }))),
      updateSmsCampaign: vi.fn().mockReturnValue(of(campaign())),
      deleteSmsCampaign: vi.fn().mockReturnValue(of(void 0)),
      activateSmsCampaign: vi.fn().mockReturnValue(of(campaign({ status: 'ACTIVE' }))),
      listSmsMessages: vi.fn().mockReturnValue(of([{ id: 'm1', deliveryStatus: 'SENT' }])),
    };
    TestBed.configureTestingModule({
      imports: [SmsCampaignsComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SmsCampaignsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads campaigns on init', () => {
    const c = make();
    expect(svc.listSmsCampaigns).toHaveBeenCalledWith(0);
    expect(c.campaigns).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error on load failure', () => {
    svc.listSmsCampaigns.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load campaigns.');
  });

  describe('selectCampaign', () => {
    it('loads messages on first select', () => {
      const c = make();
      c.selectCampaign(campaign());
      expect(svc.listSmsMessages).toHaveBeenCalledWith('sc1');
      expect(c.selected!.id).toBe('sc1');
      expect(c.messages).toHaveLength(1);
    });
    it('toggles off when re-selecting the same campaign', () => {
      const c = make();
      c.selectCampaign(campaign());
      c.selectCampaign(campaign());
      expect(c.selected).toBeNull();
    });
  });

  describe('submitSave', () => {
    it('does nothing when name or message is blank', () => {
      const c = make();
      c.openCreate();
      c.submitSave();
      expect(svc.createSmsCampaign).not.toHaveBeenCalled();
    });
    it('creates a campaign and reloads', () => {
      const c = make();
      c.openCreate();
      c.formName = 'Promo'; c.formMessage = 'Hi';
      c.submitSave();
      expect(svc.createSmsCampaign).toHaveBeenCalledWith(expect.objectContaining({ campaignName: 'Promo', message: 'Hi' }));
      expect(c.activeModal).toBeNull();
    });
    it('updates when editing', () => {
      const c = make();
      c.openEdit(campaign());
      c.formMessage = 'Updated';
      c.submitSave();
      expect(svc.updateSmsCampaign).toHaveBeenCalledWith('sc1', expect.any(Object));
    });
    it('surfaces an error on failure', () => {
      svc.createSmsCampaign.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.formName = 'P'; c.formMessage = 'M';
      c.submitSave();
      expect(c.modalError).toBe('Save failed. Please try again.');
    });
  });

  it('submitDelete deletes and clears selection if needed', () => {
    const c = make();
    c.selected = campaign();
    c.openDelete(campaign());
    c.submitDelete();
    expect(svc.deleteSmsCampaign).toHaveBeenCalledWith('sc1');
    expect(c.selected).toBeNull();
  });

  it('submitActivate replaces the campaign in place', () => {
    const c = make();
    c.openActivate(campaign());
    c.submitActivate();
    expect(svc.activateSmsCampaign).toHaveBeenCalledWith('sc1');
    expect(c.campaigns.find(x => x.id === 'sc1')!.status).toBe('ACTIVE');
  });

  describe('pagination helpers', () => {
    let c: SmsCampaignsComponent;
    beforeEach(() => {
      svc.listSmsCampaigns.mockReturnValue(page([], 50));
      c = make();
      c.total = 50;
    });
    it('totalPages/startRow/endRow compute the window', () => {
      c.page = 0;
      expect(c.totalPages).toBe(3);
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(20);
      c.page = 2;
      expect(c.startRow).toBe(41);
      expect(c.endRow).toBe(50);
    });
    it('startRow is 0 with no rows', () => {
      c.total = 0;
      expect(c.startRow).toBe(0);
    });
    it('nextPage stops at the last page; prevPage never below 0', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
      c.prevPage(); expect(c.page).toBe(1);
      c.page = 0; c.prevPage(); expect(c.page).toBe(0);
    });
  });

  it('canActivate only for PENDING / WAITING_FOR_ACTIVATION', () => {
    const c = make();
    expect(c.canActivate(campaign({ status: 'PENDING' }))).toBe(true);
    expect(c.canActivate(campaign({ status: 'WAITING_FOR_ACTIVATION' }))).toBe(true);
    expect(c.canActivate(campaign({ status: 'ACTIVE' }))).toBe(false);
  });

  it('statusVariant + deliveryVariant maps', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('WAITING_FOR_ACTIVATION')).toBe('info');
    expect(c.statusVariant('PENDING')).toBe('warning');
    expect(c.statusVariant('CLOSED')).toBe('neutral');
    expect(c.deliveryVariant('SENT')).toBe('success');
    expect(c.deliveryVariant('FAILED')).toBe('error');
    expect(c.deliveryVariant('INVALID')).toBe('error');
    expect(c.deliveryVariant('PENDING')).toBe('neutral');
  });

  it('deletingName/activatingName resolve from the list', () => {
    const c = make();
    c.campaigns = [campaign()];
    c.editingId = 'sc1';
    expect(c.deletingName()).toBe('Promo');
    expect(c.activatingName()).toBe('Promo');
    c.editingId = 'missing';
    expect(c.deletingName()).toBe('');
  });
});
