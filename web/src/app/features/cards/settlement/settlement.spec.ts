import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SettlementComponent } from './settlement';
import { CardsService, SettlementBatch, SettlementTransmission } from '../cards.service';

type Svc = Record<
  'listBatches' | 'listTransmissions' | 'closeBatch' | 'triggerExport',
  ReturnType<typeof vi.fn>
>;

function batch(over: Partial<SettlementBatch> = {}): SettlementBatch {
  return {
    id: 'b1', batchRef: 'BATCH-1', status: 'OPEN', settlementDate: '2026-01-01',
    totalAmount: 12345, itemCount: 3, openedAt: '2026-01-01', closedAt: null, ...over,
  };
}
function transmission(over: Partial<SettlementTransmission> = {}): SettlementTransmission {
  return {
    id: 't1', batchId: 'b1', scheme: 'VISA', status: 'TRANSMITTED',
    endpoint: 'sftp://x', attemptCount: 1, lastAttemptAt: null, transmittedAt: '2026-01-01', ...over,
  };
}

describe('SettlementComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listBatches: vi.fn().mockReturnValue(of([batch()])),
      listTransmissions: vi.fn().mockReturnValue(of([transmission()])),
      closeBatch: vi.fn().mockReturnValue(of(batch({ status: 'CLOSED' }))),
      triggerExport: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [SettlementComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SettlementComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads batches and transmissions on init', () => {
    const c = make();
    expect(svc.listBatches).toHaveBeenCalled();
    expect(svc.listTransmissions).toHaveBeenCalled();
    expect(c.batches).toHaveLength(1);
    expect(c.transmissions).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false when batch load fails', () => {
    svc.listBatches.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('closeBatch triggers reload', () => {
    const c = make();
    svc.listBatches.mockClear();
    c.closeBatch('b1');
    expect(svc.closeBatch).toHaveBeenCalledWith('b1');
    expect(svc.listBatches).toHaveBeenCalled();
  });

  it('triggerExport triggers reload', () => {
    const c = make();
    svc.listBatches.mockClear();
    c.triggerExport('b1');
    expect(svc.triggerExport).toHaveBeenCalledWith('b1');
    expect(svc.listBatches).toHaveBeenCalled();
  });

  it('toggleExpand expands then collapses the same batch', () => {
    const c = make();
    c.toggleExpand('b1');
    expect(c.expandedBatch).toBe('b1');
    c.toggleExpand('b1');
    expect(c.expandedBatch).toBeNull();
  });

  it('batchTransmissions filters by batch id', () => {
    const c = make();
    c.transmissions = [transmission({ id: 't1', batchId: 'b1' }), transmission({ id: 't2', batchId: 'b2' })];
    expect(c.batchTransmissions('b1').map(t => t.id)).toEqual(['t1']);
  });

  it('statusVariant maps batch + transmission statuses', () => {
    const c = make();
    expect(c.statusVariant('OPEN')).toBe('info');
    expect(c.statusVariant('CLOSED')).toBe('warning');
    expect(c.statusVariant('SETTLED')).toBe('success');
    expect(c.statusVariant('FAILED')).toBe('error');
    expect(c.statusVariant('TRANSMITTED')).toBe('success');
    expect(c.statusVariant('ZZZ')).toBe('neutral');
  });

  it('totalAmount renders cents as a 2dp string', () => {
    const c = make();
    expect(c.totalAmount(batch({ totalAmount: 12345 }))).toBe('123.45');
  });
});
