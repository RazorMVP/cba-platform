import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TreasuryLiquidityComponent } from './liquidity';
import {
  TreasuryService, LiquidityPosition, CashFlowEntry, ReserveRequirement, LiquiditySnapshot,
} from './treasury.service';

type Svc = Record<
  'getLiquidityPositions' | 'getCashFlowForecast' | 'listReserveRequirements' |
  'createReserveRequirement' | 'updateReserveRequirement' | 'deleteReserveRequirement' |
  'getLiquiditySnapshots' | 'takeSnapshot',
  ReturnType<typeof vi.fn>
>;

function position(over: Partial<LiquidityPosition> = {}): LiquidityPosition {
  return {
    currency: 'USD', cashOnHand: 1000, placementsDeployed: 200, interbankLending: 50,
    interbankBorrowing: 30, netLiquidityPosition: 820, reserveRequirement: 100,
    surplusDeficit: 720, alertLevel: 'OK', asOfDate: '2026-06-01', ...over,
  };
}
const cashFlow: CashFlowEntry[] = [
  { date: '2026-06-02', type: 'PLACEMENT_MATURITY', reference: 'P1', amount: 500, currency: 'USD', direction: 'INFLOW' },
  { date: '2026-06-03', type: 'LOAN_REPAYMENT', reference: 'L1', amount: 200, currency: 'USD', direction: 'OUTFLOW' },
];
function reserve(over: Partial<ReserveRequirement> = {}): ReserveRequirement {
  return { id: 'rr1', currencyCode: 'USD', minimumBalance: 1000, active: true, ...over };
}
const snapshot: LiquiditySnapshot = {
  id: 'sn1', snapshotDate: '2026-06-01', currencyCode: 'USD', cashOnHand: 1000,
  placementsDeployed: 200, interbankLending: 50, interbankBorrowing: 30,
  netLiquidityPosition: 820, createdAt: '2026-06-01',
};

describe('TreasuryLiquidityComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getLiquidityPositions: vi.fn().mockReturnValue(of([position()])),
      getCashFlowForecast: vi.fn().mockReturnValue(of(cashFlow)),
      listReserveRequirements: vi.fn().mockReturnValue(of([reserve()])),
      createReserveRequirement: vi.fn().mockReturnValue(of(reserve({ id: 'rr2' }))),
      updateReserveRequirement: vi.fn().mockReturnValue(of(reserve())),
      deleteReserveRequirement: vi.fn().mockReturnValue(of(void 0)),
      getLiquiditySnapshots: vi.fn().mockReturnValue(of([snapshot])),
      takeSnapshot: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [TreasuryLiquidityComponent],
      providers: [{ provide: TreasuryService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TreasuryLiquidityComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads positions and reserves on init', () => {
    const c = make();
    expect(svc.getLiquidityPositions).toHaveBeenCalled();
    expect(svc.listReserveRequirements).toHaveBeenCalled();
    expect(c.positions).toHaveLength(1);
    expect(c.reserves).toHaveLength(1);
  });

  it('selectedPosition + currenciesAvailable derive from loaded positions', () => {
    const c = make();
    c.positions = [position({ currency: 'USD' }), position({ currency: 'KES' })];
    c.selectedCurrency = 'KES';
    expect(c.selectedPosition?.currency).toBe('KES');
    expect(c.currenciesAvailable).toEqual(['USD', 'KES']);
  });

  it('alertClass + alertLabel map alert levels', () => {
    const c = make();
    expect(c.alertClass('BREACH')).toBe('alert-breach');
    expect(c.alertClass('WARN')).toBe('alert-warn');
    expect(c.alertClass('OK')).toBe('alert-ok');
    expect(c.alertLabel('BREACH')).toBe('Below Reserve');
    expect(c.alertLabel('WARN')).toBe('Near Threshold');
    expect(c.alertLabel('OK')).toBe('Adequate');
  });

  describe('selectTab', () => {
    it('lazy-loads cash flow on first cashflow visit', () => {
      const c = make();
      c.selectTab('cashflow');
      expect(svc.getCashFlowForecast).toHaveBeenCalledWith('USD', 30);
      expect(c.cashFlow).toHaveLength(2);
      c.selectTab('position');
      c.selectTab('cashflow');
      expect(svc.getCashFlowForecast).toHaveBeenCalledTimes(1);
    });

    it('lazy-loads snapshots on first history visit', () => {
      const c = make();
      c.selectTab('history');
      expect(svc.getLiquiditySnapshots).toHaveBeenCalledWith('USD');
      expect(c.snapshots).toHaveLength(1);
    });
  });

  it('cashFlowInflows / cashFlowOutflows + typeLabel compute correctly', () => {
    const c = make();
    c.cashFlow = cashFlow;
    expect(c.cashFlowInflows()).toBe(500);
    expect(c.cashFlowOutflows()).toBe(200);
    expect(c.typeLabel('PLACEMENT_MATURITY')).toBe('Placement Maturity');
    expect(c.typeLabel('UNKNOWN')).toBe('UNKNOWN');
  });

  describe('reserves CRUD', () => {
    it('openEditReserve seeds the form and marks editing', () => {
      const c = make();
      c.openEditReserve(reserve({ id: 'rr9', currencyCode: 'KES', minimumBalance: 777 }));
      expect(c.editingReserve?.id).toBe('rr9');
      expect(c.reserveForm.currencyCode).toBe('KES');
      expect(c.reserveForm.minimumBalance).toBe(777);
      expect(c.activeModal).toBe('editReserve');
    });

    it('saveReserve creates when not editing and reloads', () => {
      const c = make();
      c.openCreateReserve();
      c.reserveForm = { currencyCode: 'USD', minimumBalance: 5000 };
      c.saveReserve();
      expect(svc.createReserveRequirement).toHaveBeenCalledWith({ currencyCode: 'USD', minimumBalance: 5000 });
      expect(svc.listReserveRequirements).toHaveBeenCalledTimes(2);
      expect(c.activeModal).toBeNull();
    });

    it('saveReserve updates when editing', () => {
      const c = make();
      c.openEditReserve(reserve({ id: 'rr9' }));
      c.saveReserve();
      expect(svc.updateReserveRequirement).toHaveBeenCalledWith('rr9', expect.anything());
    });

    it('confirmDeleteReserve deletes the staged reserve and reloads', () => {
      const c = make();
      c.openDeleteReserve(reserve({ id: 'rr9' }));
      c.confirmDeleteReserve();
      expect(svc.deleteReserveRequirement).toHaveBeenCalledWith('rr9');
      expect(svc.listReserveRequirements).toHaveBeenCalledTimes(2);
    });

    it('confirmDeleteReserve is a no-op without a staged reserve', () => {
      const c = make();
      c.deletingReserve = null;
      c.confirmDeleteReserve();
      expect(svc.deleteReserveRequirement).not.toHaveBeenCalled();
    });
  });

  describe('snapshots', () => {
    it('triggerSnapshot takes a snapshot and reloads only when on history tab', () => {
      const c = make();
      c.activeTab = 'history';
      c.triggerSnapshot();
      expect(svc.takeSnapshot).toHaveBeenCalled();
      expect(svc.getLiquiditySnapshots).toHaveBeenCalled();
      expect(c.takingSnapshot).toBe(false);
    });

    it('triggerSnapshot does not reload snapshots when off the history tab', () => {
      const c = make();
      c.activeTab = 'position';
      c.triggerSnapshot();
      expect(svc.takeSnapshot).toHaveBeenCalled();
      expect(svc.getLiquiditySnapshots).not.toHaveBeenCalled();
    });
  });

  it('fmt + pct format numbers safely', () => {
    const c = make();
    expect(c.fmt(undefined)).toBe('—');
    expect(c.fmt(1000, 'USD')).toContain('1,000');
    expect(c.pct(50, 0)).toBe('—');
    expect(c.pct(50, 200)).toBe('25.0%');
  });

  it('keeps loading flags false even when a load fails', () => {
    svc.getLiquidityPositions.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadingPositions).toBe(false);
  });
});
