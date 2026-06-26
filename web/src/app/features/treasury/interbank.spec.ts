import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TreasuryInterbankComponent } from './interbank';
import { TreasuryService, TreasuryInterbankPosition } from './treasury.service';

type Svc = Record<
  'listPositions' | 'createPosition' | 'updatePosition' | 'commandPosition' | 'deletePosition',
  ReturnType<typeof vi.fn>
>;

function position(over: Partial<TreasuryInterbankPosition> = {}): TreasuryInterbankPosition {
  return {
    id: 'x1', reference: 'IB-1', counterpartyName: 'Peer Bank', direction: 'LENDING',
    amount: 50000, currencyCode: 'USD', interestRate: 4, startDate: '2026-01-01',
    status: 'ACTIVE', createdAt: '2026-01-01', updatedAt: '2026-01-01', ...over,
  };
}

describe('TreasuryInterbankComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listPositions: vi.fn().mockReturnValue(of([position()])),
      createPosition: vi.fn().mockReturnValue(of(position({ id: 'x2' }))),
      updatePosition: vi.fn().mockReturnValue(of(position())),
      commandPosition: vi.fn().mockReturnValue(of(position({ status: 'SETTLED' }))),
      deletePosition: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [TreasuryInterbankComponent],
      providers: [{ provide: TreasuryService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TreasuryInterbankComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads positions on init', () => {
    const c = make();
    expect(svc.listPositions).toHaveBeenCalled();
    expect(c.positions).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('surfaces a backend error message on load failure', () => {
    svc.listPositions.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'boom' }] } })));
    const c = make();
    expect(c.error).toBe('boom');
    expect(c.loading).toBe(false);
  });

  it('availableCommands offers settle/cancel only for ACTIVE positions', () => {
    const c = make();
    expect(c.availableCommands(position({ status: 'ACTIVE' })).map(x => x.cmd)).toEqual(['settle', 'cancel']);
    expect(c.availableCommands(position({ status: 'SETTLED' }))).toEqual([]);
  });

  it('directionClass + statusClass map correctly', () => {
    const c = make();
    expect(c.directionClass('LENDING')).toBe('dir-lending');
    expect(c.directionClass('BORROWING')).toBe('dir-borrowing');
    expect(c.statusClass('ACTIVE')).toBe('badge-success');
    expect(c.statusClass('SETTLED')).toBe('badge-info');
    expect(c.statusClass('CANCELLED')).toBe('badge-neutral');
  });

  it('openEdit seeds the form from the position', () => {
    const c = make();
    c.openEdit(position({ id: 'x9', reference: 'IB-9', direction: 'BORROWING', amount: 9000 }));
    expect(c.activeModal).toBe('edit');
    expect(c.editingId).toBe('x9');
    expect(c.formDirection).toBe('BORROWING');
    expect(c.formAmount).toBe(9000);
  });

  describe('submitModal', () => {
    function fillValidForm(c: TreasuryInterbankComponent) {
      c.formReference = 'IB-X';
      c.formCounterparty = 'Bank';
      c.formAmount = 1000;
      c.formRate = 4;
      c.formStartDate = '2026-01-01';
    }

    it('validates required fields', () => {
      const c = make();
      c.openCreate();
      c.submitModal();
      expect(c.modalError).toBe('Please fill in all required fields.');
      expect(svc.createPosition).not.toHaveBeenCalled();
    });

    it('creates a position and reloads', () => {
      const c = make();
      c.openCreate();
      fillValidForm(c);
      c.submitModal();
      expect(svc.createPosition).toHaveBeenCalled();
      expect(svc.listPositions).toHaveBeenCalledTimes(2);
      expect(c.activeModal).toBeNull();
    });

    it('updates an existing position when editing', () => {
      const c = make();
      c.openEdit(position({ id: 'x9' }));
      fillValidForm(c);
      c.submitModal();
      expect(svc.updatePosition).toHaveBeenCalledWith('x9', expect.anything());
    });

    it('surfaces a save error', () => {
      svc.createPosition.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'nope' }] } })));
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
    c.openCommand(position({ id: 'x1' }), 'settle');
    expect(c.commandLabel).toBe('Settle');
    c.submitCommand();
    expect(svc.commandPosition).toHaveBeenCalledWith('x1', 'settle');
    expect(svc.listPositions).toHaveBeenCalledTimes(2);
  });

  it('submitDelete deletes the staged position and reloads', () => {
    const c = make();
    c.openDelete(position({ id: 'x1' }));
    c.submitDelete();
    expect(svc.deletePosition).toHaveBeenCalledWith('x1');
    expect(svc.listPositions).toHaveBeenCalledTimes(2);
  });
});
