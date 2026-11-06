import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OpenBankingListComponent } from './open-banking-list';
import { OpenBankingService, Consent } from './open-banking.service';

type Svc = Record<'listConsents', ReturnType<typeof vi.fn>>;

function consent(over: Partial<Consent> = {}): Consent {
  return {
    consentId: 'ob-1', consentType: 'AISP', customerId: 'c1', customerName: 'Jo Obi',
    tppClientId: 'org-1', tppName: 'Acme TPP', scopes: ['accounts_read', 'balances_read'],
    status: 'AUTHORISED', expiryDate: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: null,
    initiation: null, paymentId: null, consumedAt: null,
    ...over,
  };
}

function page(content: Consent[], totalElements = content.length, totalPages = 1) {
  return of({ content, totalElements, totalPages, size: 20, number: 0 });
}

describe('OpenBankingListComponent', () => {
  let svc: Svc;
  let query: Record<string, string>;

  beforeEach(() => {
    query = {};
    svc = { listConsents: vi.fn().mockReturnValue(page([consent()])) };
    TestBed.configureTestingModule({
      imports: [OpenBankingListComponent],
      providers: [
        provideRouter([]),
        { provide: OpenBankingService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { get queryParamMap() { return convertToParamMap(query); } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(OpenBankingListComponent);
    fixture.detectChanges();
    return { c: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('loads the first page on init and renders names', () => {
    const { c, el } = make();
    expect(svc.listConsents).toHaveBeenCalledWith(
      { type: undefined, status: undefined, tppClientId: undefined, customerId: undefined }, 0, 20);
    expect(c.consents).toHaveLength(1);
    expect(c.loading).toBe(false);
    expect(el.textContent).toContain('Jo Obi');
    expect(el.textContent).toContain('Acme TPP');
  });

  it('takes the TPP filter from the URL (link from the TPP registry)', () => {
    query = { tppClientId: 'org-9' };
    const { c } = make();
    expect(c.tppClientId).toBe('org-9');
    expect(svc.listConsents).toHaveBeenCalledWith(expect.objectContaining({ tppClientId: 'org-9' }), 0, 20);
    c.clearTpp();
    expect(svc.listConsents).toHaveBeenLastCalledWith(expect.objectContaining({ tppClientId: undefined }), 0, 20);
  });

  it('type and status filters reload from page 0 on the server', () => {
    const { c } = make();
    c.page = 3;
    c.setType('PISP');
    expect(svc.listConsents).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'PISP' }), 0, 20);
    c.statusFilter = 'CONSUMED';
    c.setStatus();
    expect(svc.listConsents).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: 'PISP', status: 'CONSUMED' }), 0, 20);
  });

  it('pages within bounds', () => {
    svc.listConsents.mockReturnValue(page([consent()], 45, 3));
    const { c } = make();
    c.prevPage();
    expect(c.page).toBe(0);
    c.nextPage();
    c.nextPage();
    expect(c.page).toBe(2);
    c.nextPage();
    expect(c.page).toBe(2);
  });

  it('sets an error when loading fails', () => {
    svc.listConsents.mockReturnValue(throwError(() => new Error('x')));
    const { c } = make();
    expect(c.error).toBe('Failed to load consents.');
    expect(c.loading).toBe(false);
  });

  it('statusVariant maps consent status to badge variants', () => {
    const { c } = make();
    expect(c.statusVariant('AWAITING_AUTHORISATION')).toBe('warning');
    expect(c.statusVariant('AUTHORISED')).toBe('success');
    expect(c.statusVariant('CONSUMED')).toBe('info');
    expect(c.statusVariant('REVOKED')).toBe('neutral');
  });
});
