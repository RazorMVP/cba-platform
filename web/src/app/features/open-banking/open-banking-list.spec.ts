import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OpenBankingListComponent } from './open-banking-list';
import { OpenBankingService, Consent } from './open-banking.service';

type Svc = Record<'listConsents', ReturnType<typeof vi.fn>>;

function consent(over: Partial<Consent> = {}): Consent {
  return {
    id: 'cn1', consentId: 'OB-1', customerId: 'c1', customerName: 'Jo',
    tppName: 'Acme TPP', tppClientId: 'tpp1', consentType: 'AISP',
    scopes: ['accounts_read', 'balances_read'], status: 'AUTHORISED',
    createdAt: '2026-01-01', expirationDateTime: null, authorisedAt: '2026-01-02', revokedAt: null,
    ...over,
  };
}

describe('OpenBankingListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { listConsents: vi.fn().mockReturnValue(of([consent()])) };
    TestBed.configureTestingModule({
      imports: [OpenBankingListComponent],
      providers: [provideRouter([]), { provide: OpenBankingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(OpenBankingListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads consents on init', () => {
    const c = make();
    expect(svc.listConsents).toHaveBeenCalled();
    expect(c.consents).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listConsents.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load consents.');
    expect(c.loading).toBe(false);
  });

  it('filtered narrows by type then status', () => {
    const c = make();
    c.consents = [
      consent({ id: 'a', consentType: 'AISP', status: 'AUTHORISED' }),
      consent({ id: 'b', consentType: 'PISP', status: 'AUTHORISED' }),
      consent({ id: 'd', consentType: 'PISP', status: 'REVOKED' }),
    ];
    c.typeFilter = 'PISP';
    expect(c.filtered.map(x => x.id)).toEqual(['b', 'd']);
    c.statusFilter = 'REVOKED';
    expect(c.filtered.map(x => x.id)).toEqual(['d']);
    c.typeFilter = '';
    c.statusFilter = '';
    expect(c.filtered).toHaveLength(3);
  });

  it('typeVariant maps consent types to badge variants', () => {
    const c = make();
    expect(c.typeVariant('AISP')).toBe('info');
    expect(c.typeVariant('PISP')).toBe('success');
    expect(c.typeVariant('CBPII')).toBe('warning');
  });

  it('statusVariant maps consent status to badge variants', () => {
    const c = make();
    expect(c.statusVariant('AWAITING_AUTHORISATION')).toBe('warning');
    expect(c.statusVariant('AUTHORISED')).toBe('success');
    expect(c.statusVariant('REVOKED')).toBe('neutral');
  });
});
