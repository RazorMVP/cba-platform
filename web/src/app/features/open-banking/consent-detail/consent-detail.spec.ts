import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConsentDetailComponent } from './consent-detail';
import { OpenBankingService, Consent } from '../open-banking.service';

type Svc = Record<'getConsent' | 'authoriseConsent' | 'revokeConsent', ReturnType<typeof vi.fn>>;

function consent(over: Partial<Consent> = {}): Consent {
  return {
    id: 'cn1', consentId: 'OB-1', customerId: 'c1', customerName: 'Jo',
    tppName: 'Acme TPP', tppClientId: 'tpp1', consentType: 'PISP',
    scopes: ['payments'], status: 'AWAITING_AUTHORISATION',
    createdAt: '2026-01-01', expirationDateTime: null, authorisedAt: null, revokedAt: null,
    ...over,
  };
}

describe('ConsentDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getConsent: vi.fn().mockReturnValue(of(consent())),
      authoriseConsent: vi.fn().mockReturnValue(of(consent({ status: 'AUTHORISED' }))),
      revokeConsent: vi.fn().mockReturnValue(of(consent({ status: 'REVOKED' }))),
    };
    TestBed.configureTestingModule({
      imports: [ConsentDetailComponent],
      providers: [
        provideRouter([]),
        { provide: OpenBankingService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'cn1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ConsentDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the consent from the route id on init', () => {
    const c = make();
    expect(svc.getConsent).toHaveBeenCalledWith('cn1');
    expect(c.consent?.id).toBe('cn1');
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.getConsent.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load consent.');
    expect(c.loading).toBe(false);
  });

  describe('confirm flow', () => {
    it('openConfirm stages the action', () => {
      const c = make();
      c.openConfirm('authorise');
      expect(c.confirmModal).toBe('authorise');
      expect(c.working).toBe(false);
    });

    it('confirm authorises and updates the consent', () => {
      const c = make();
      c.openConfirm('authorise');
      c.confirm();
      expect(svc.authoriseConsent).toHaveBeenCalledWith('cn1');
      expect(c.consent?.status).toBe('AUTHORISED');
      expect(c.confirmModal).toBeNull();
    });

    it('confirm revokes when revoke is staged', () => {
      const c = make();
      c.openConfirm('revoke');
      c.confirm();
      expect(svc.revokeConsent).toHaveBeenCalledWith('cn1');
      expect(c.consent?.status).toBe('REVOKED');
    });

    it('confirm is a no-op without a staged action', () => {
      const c = make();
      c.confirmModal = null;
      c.confirm();
      expect(svc.authoriseConsent).not.toHaveBeenCalled();
      expect(svc.revokeConsent).not.toHaveBeenCalled();
    });

    it('confirm surfaces an error on failure', () => {
      svc.authoriseConsent.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openConfirm('authorise');
      c.confirm();
      expect(c.actionError).toBe('Action failed.');
      expect(c.working).toBe(false);
    });

    it('closeConfirm does nothing while working', () => {
      const c = make();
      c.openConfirm('revoke');
      c.working = true;
      c.closeConfirm();
      expect(c.confirmModal).toBe('revoke');
    });
  });

  it('statusVariant maps consent status to badge variants', () => {
    const c = make();
    expect(c.statusVariant('AWAITING_AUTHORISATION')).toBe('warning');
    expect(c.statusVariant('AUTHORISED')).toBe('success');
    expect(c.statusVariant('EXPIRED')).toBe('neutral');
  });
});
