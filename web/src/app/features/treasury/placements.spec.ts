import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TreasuryPlacementsComponent } from './placements';
import { TreasuryService, TreasuryPlacement } from './treasury.service';

type Svc = Record<
  'listPlacements' | 'createPlacement' | 'updatePlacement' | 'commandPlacement' | 'deletePlacement',
  ReturnType<typeof vi.fn>
>;

function placement(over: Partial<TreasuryPlacement> = {}): TreasuryPlacement {
  return {
    id: 'p1', reference: 'PL-1', counterpartyName: 'Big Bank', placementType: 'FIXED_DEPOSIT',
    principal: 100000, interestRate: 5, currencyCode: 'USD', startDate: '2026-01-01',
    maturityDate: '2026-06-01', status: 'PENDING', createdAt: '2026-01-01', updatedAt: '2026-01-01',
    ...over,
  };
}

describe('TreasuryPlacementsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listPlacements: vi.fn().mockReturnValue(of([placement()])),
      createPlacement: vi.fn().mockReturnValue(of(placement({ id: 'p2' }))),
      updatePlacement: vi.fn().mockReturnValue(of(placement())),
      commandPlacement: vi.fn().mockReturnValue(of(placement({ status: 'ACTIVE' }))),
      deletePlacement: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [TreasuryPlacementsComponent],
      providers: [{ provide: TreasuryService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TreasuryPlacementsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads placements on init', () => {
    const c = make();
    expect(svc.listPlacements).toHaveBeenCalled();
    expect(c.placements).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('surfaces a backend error message on load failure', () => {
    svc.listPlacements.mockReturnValue(
      throwError(() => ({ error: { errors: [{ message: 'boom' }] } })),
    );
    const c = make();
    expect(c.error).toBe('boom');
    expect(c.loading).toBe(false);
  });

  it('availableCommands reflects the placement status', () => {
    const c = make();
    expect(c.availableCommands(placement({ status: 'PENDING' })).map(x => x.cmd)).toEqual(['activate']);
    expect(c.availableCommands(placement({ status: 'ACTIVE' })).map(x => x.cmd)).toEqual(['mature', 'cancel']);
    expect(c.availableCommands(placement({ status: 'MATURED' }))).toEqual([]);
  });

  it('statusClass + typeLabel map correctly', () => {
    const c = make();
    expect(c.statusClass('PENDING')).toBe('badge-warning');
    expect(c.statusClass('ACTIVE')).toBe('badge-success');
    expect(c.statusClass('CANCELLED')).toBe('badge-neutral');
    expect(c.typeLabel('FIXED_DEPOSIT')).toBe('FIXED DEPOSIT');
  });

  describe('openEdit', () => {
    it('seeds the form from the placement', () => {
      const c = make();
      c.openEdit(placement({ id: 'p9', reference: 'PL-9', principal: 250, interestRate: 3 }));
      expect(c.activeModal).toBe('edit');
      expect(c.editingId).toBe('p9');
      expect(c.formReference).toBe('PL-9');
      expect(c.formPrincipal).toBe(250);
      expect(c.formRate).toBe(3);
    });
  });

  describe('submitModal', () => {
    it('validates required fields before calling the service', () => {
      const c = make();
      c.openCreate();
      c.submitModal();
      expect(c.modalError).toBe('Please fill in all required fields.');
      expect(svc.createPlacement).not.toHaveBeenCalled();
    });

    function fillValidForm(c: TreasuryPlacementsComponent) {
      c.formReference = 'PL-X';
      c.formCounterparty = 'Bank';
      c.formPrincipal = 1000;
      c.formRate = 4;
      c.formStartDate = '2026-01-01';
      c.formMaturityDate = '2026-06-01';
    }

    it('creates a placement and reloads', () => {
      const c = make();
      c.openCreate();
      fillValidForm(c);
      c.submitModal();
      expect(svc.createPlacement).toHaveBeenCalled();
      const req = svc.createPlacement.mock.calls[0][0];
      expect(req.reference).toBe('PL-X');
      expect(svc.listPlacements).toHaveBeenCalledTimes(2);
      expect(c.activeModal).toBeNull();
    });

    it('updates an existing placement when editing', () => {
      const c = make();
      c.openEdit(placement({ id: 'p9' }));
      fillValidForm(c);
      c.submitModal();
      expect(svc.updatePlacement).toHaveBeenCalledWith('p9', expect.anything());
    });

    it('surfaces a save error', () => {
      svc.createPlacement.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'nope' }] } })));
      const c = make();
      c.openCreate();
      fillValidForm(c);
      c.submitModal();
      expect(c.modalError).toBe('nope');
      expect(c.modalWorking).toBe(false);
    });
  });

  it('submitCommand runs the staged command and reloads', () => {
    const c = make();
    c.openCommand(placement({ id: 'p1' }), 'activate');
    expect(c.activeModal).toBe('command');
    expect(c.commandLabel).toBe('Activate');
    c.submitCommand();
    expect(svc.commandPlacement).toHaveBeenCalledWith('p1', 'activate');
    expect(svc.listPlacements).toHaveBeenCalledTimes(2);
    expect(c.activeModal).toBeNull();
  });

  it('submitDelete deletes the staged placement and reloads', () => {
    const c = make();
    c.openDelete(placement({ id: 'p1', reference: 'PL-1' }));
    expect(c.deletingId).toBe('p1');
    c.submitDelete();
    expect(svc.deletePlacement).toHaveBeenCalledWith('p1');
    expect(svc.listPlacements).toHaveBeenCalledTimes(2);
  });

  it('closeModal does nothing while working', () => {
    const c = make();
    c.openCreate();
    c.modalWorking = true;
    c.closeModal();
    expect(c.activeModal).toBe('create');
  });
});
