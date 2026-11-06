import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConsentDetailComponent } from './consent-detail';
import { OpenBankingService, Consent } from '../open-banking.service';

type Svc = Record<'getConsent' | 'revokeConsent', ReturnType<typeof vi.fn>>;

function consent(over: Partial<Consent> = {}): Consent {
  return {
    consentId: 'ob-1', consentType: 'PISP', customerId: 'c1', customerName: 'Jo Obi',
    tppClientId: 'org-1', tppName: 'Acme TPP', scopes: ['payments'],
    status: 'AUTHORISED', expiryDate: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: null,
    initiation: { instructedAmount: 25, currency: 'USD', creditorAccountId: 'acc-2', debtorAccountId: null, reference: 'INV-1' },
    paymentId: null, consumedAt: null,
    ...over,
  };
}

describe('ConsentDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getConsent: vi.fn().mockReturnValue(of(consent())),
      revokeConsent: vi.fn().mockReturnValue(of(consent({ status: 'REVOKED' }))),
    };
    TestBed.configureTestingModule({
      imports: [ConsentDetailComponent],
      providers: [
        provideRouter([]),
        { provide: OpenBankingService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ob-1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ConsentDetailComponent);
    fixture.detectChanges();
    return { c: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('loads the consent by the consentId in the route and shows the authorised payment', () => {
    const { c, el } = make();
    expect(svc.getConsent).toHaveBeenCalledWith('ob-1');
    expect(c.consent?.consentId).toBe('ob-1');
    expect(el.textContent).toContain('Authorised payment');
    expect(el.textContent).toContain('INV-1');
  });

  it('offers no Authorise action — that is the customer’s step', () => {
    svc.getConsent.mockReturnValue(of(consent({ status: 'AWAITING_AUTHORISATION' })));
    const { el } = make();
    const buttons = Array.from(el.querySelectorAll('button')).map(b => b.textContent ?? '');
    expect(buttons.some(t => t.includes('Authorise'))).toBe(false);
    expect(buttons.some(t => t.includes('Revoke'))).toBe(true);
  });

  it('sets an error when loading fails', () => {
    svc.getConsent.mockReturnValue(throwError(() => new Error('x')));
    const { c } = make();
    expect(c.error).toBe('Failed to load consent.');
    expect(c.loading).toBe(false);
  });

  it('canRevoke only while the TPP can still use the consent', () => {
    const { c } = make();
    expect(c.canRevoke).toBe(true);
    c.consent = consent({ status: 'CONSUMED' });
    expect(c.canRevoke).toBe(false);
    c.consent = consent({ status: 'REVOKED' });
    expect(c.canRevoke).toBe(false);
  });

  describe('revoke', () => {
    it('needs a reason, then revokes with it trimmed', () => {
      const { c } = make();
      c.openRevoke();
      c.reason = '   ';
      c.revoke();
      expect(svc.revokeConsent).not.toHaveBeenCalled();

      c.reason = '  customer asked at branch ';
      c.revoke();
      expect(svc.revokeConsent).toHaveBeenCalledWith('ob-1', 'customer asked at branch');
      expect(c.consent?.status).toBe('REVOKED');
      expect(c.revokeOpen).toBe(false);
    });

    it('shows the server message on failure', () => {
      svc.revokeConsent.mockReturnValue(throwError(() => new HttpErrorResponse({
        status: 409, error: { errors: [{ code: 'CONSENT_CONSUMED', message: 'already used' }] },
      })));
      const { c } = make();
      c.openRevoke();
      c.reason = 'x';
      c.revoke();
      expect(c.actionError).toBe('already used');
      expect(c.working).toBe(false);
    });

    it('closeRevoke does nothing while working', () => {
      const { c } = make();
      c.openRevoke();
      c.working = true;
      c.closeRevoke();
      expect(c.revokeOpen).toBe(true);
    });
  });
});
