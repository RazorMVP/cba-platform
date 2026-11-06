import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OpenBankingComponent } from './open-banking';
import { AdminService, TppOrganization } from './admin.service';

type Svc = Record<'listTpps' | 'approveTpp' | 'rejectTpp' | 'suspendTpp' | 'reactivateTpp', ReturnType<typeof vi.fn>>;

function tpp(over: Partial<TppOrganization> = {}): TppOrganization {
  return {
    id: 'org1', organizationName: 'Acme Fintech', status: 'PENDING_REVIEW', tier: 'BASIC',
    environment: 'SANDBOX', createdAt: '2026-01-01T00:00:00Z', totalApiCalls: 0,
    applicationStatus: 'PENDING_REVIEW', businessType: 'Lending', useCase: 'Affordability checks',
    activeConsents: 3, suspensionReason: null, ...over,
  };
}

describe('OpenBankingComponent (TPP registry)', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listTpps: vi.fn().mockReturnValue(of([tpp()])),
      approveTpp: vi.fn().mockReturnValue(of(undefined)),
      rejectTpp: vi.fn().mockReturnValue(of(undefined)),
      suspendTpp: vi.fn().mockReturnValue(of(tpp({ status: 'SUSPENDED' }))),
      reactivateTpp: vi.fn().mockReturnValue(of(tpp({ status: 'PRODUCTION' }))),
    };
    TestBed.configureTestingModule({
      imports: [OpenBankingComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(OpenBankingComponent);
    fixture.detectChanges();
    return { c: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('loads partner organisations and shows the pending application', () => {
    const { c, el } = make();
    expect(svc.listTpps).toHaveBeenCalled();
    expect(c.tpps).toHaveLength(1);
    expect(el.textContent).toContain('Acme Fintech');
    expect(el.textContent).toContain('Affordability checks');
    expect(el.textContent).not.toContain('Register TPP');
  });

  it('flags an error on load failure', () => {
    svc.listTpps.mockReturnValue(throwError(() => new Error('x')));
    const { c } = make();
    expect(c.error).toBe('Failed to load TPPs.');
  });

  it('approve calls the partner approve endpoint and reloads', () => {
    const { c } = make();
    c.open('approve', tpp());
    c.submit();
    expect(svc.approveTpp).toHaveBeenCalledWith('org1');
    expect(svc.listTpps).toHaveBeenCalledTimes(2);
    expect(c.activeModal).toBeNull();
  });

  it('suspend needs a reason, then sends it trimmed', () => {
    const { c } = make();
    c.open('suspend', tpp({ status: 'PRODUCTION' }));
    c.submit();
    expect(svc.suspendTpp).not.toHaveBeenCalled();
    c.reason = '  fraud report ';
    c.submit();
    expect(svc.suspendTpp).toHaveBeenCalledWith('org1', 'fraud report');
  });

  it('reactivate and reject route to their endpoints', () => {
    const { c } = make();
    c.open('reactivate', tpp({ status: 'SUSPENDED' }));
    c.submit();
    expect(svc.reactivateTpp).toHaveBeenCalledWith('org1');
    c.open('reject', tpp());
    c.submit();
    expect(svc.rejectTpp).toHaveBeenCalledWith('org1');
  });

  it('shows the server message when an action is refused', () => {
    svc.reactivateTpp.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { errors: [{ code: 'PARTNER_NOT_SUSPENDED', message: 'not suspended' }] },
    })));
    const { c } = make();
    c.open('reactivate', tpp());
    c.submit();
    expect(c.modalError).toBe('not suspended');
    expect(c.modalWorking).toBe(false);
  });

  it('statusVariant maps partner statuses', () => {
    const { c } = make();
    expect(c.statusVariant('PRODUCTION')).toBe('success');
    expect(c.statusVariant('PENDING_REVIEW')).toBe('warning');
    expect(c.statusVariant('SUSPENDED')).toBe('error');
    expect(c.statusVariant('SANDBOX')).toBe('info');
  });
});
