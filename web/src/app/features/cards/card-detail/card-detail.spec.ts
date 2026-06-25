import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CardDetailComponent } from './card-detail';
import { CardsService, Card, CardBalance, CardLimit, AuthorizationLog } from '../cards.service';

type Svc = Record<
  'getCard' | 'getCardBalance' | 'listAuthorizations' | 'updateCardLimits' | 'commandCard',
  ReturnType<typeof vi.fn>
>;

function card(over: Partial<Card> = {}): Card {
  return {
    id: 'card-1', panPrefix: '457312', panSuffix: '1234', expiryDate: '2612',
    cardType: 'DEBIT', status: 'ACTIVE', virtualFlag: false, customerId: 'cust-1',
    linkedEntityId: 'acc-1', productId: 'p1', productName: 'Classic Debit',
    pinRetryCount: 0, createdAt: '2026-01-01', updatedAt: '2026-01-01', ...over,
  };
}
const balance: CardBalance = { availableBalance: 5000, cardType: 'DEBIT' };
const limit: CardLimit = {
  id: 'l1', cardId: 'card-1', dailyPurchaseLimit: 100, dailyWithdrawalLimit: 50,
  perTxnLimit: 25, monthlyLimit: 1000, currencyCode: 'USD',
};
function auth(over: Partial<AuthorizationLog> = {}): AuthorizationLog {
  return {
    id: 'a1', cardId: 'card-1', stan: '000001', rrn: 'rrn1', mti: '0100',
    processingCode: '000000', amount: 1000, currencyCode: '840', responseCode: '00',
    entryMode: 'CHIP', merchantId: 'm1', merchantName: 'Shop', mcc: '5411',
    fraudScore: 5, decision: 'APPROVE', createdAt: '2026-01-01', ...over,
  };
}

describe('CardDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getCard: vi.fn().mockReturnValue(of(card())),
      getCardBalance: vi.fn().mockReturnValue(of(balance)),
      listAuthorizations: vi.fn().mockReturnValue(of([auth()])),
      updateCardLimits: vi.fn().mockReturnValue(of({ ...limit, dailyPurchaseLimit: 999 })),
      commandCard: vi.fn().mockReturnValue(of(card({ status: 'BLOCKED' }))),
    };
    TestBed.configureTestingModule({
      imports: [CardDetailComponent],
      providers: [
        provideRouter([]),
        { provide: CardsService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'card-1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CardDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the card + balance + authorizations from the route id on init', () => {
    const c = make();
    expect(svc.getCard).toHaveBeenCalledWith('card-1');
    expect(svc.getCardBalance).toHaveBeenCalledWith('card-1');
    expect(svc.listAuthorizations).toHaveBeenCalledWith('card-1');
    expect(c.card?.id).toBe('card-1');
    expect(c.balance?.availableBalance).toBe(5000);
    expect(c.auths).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  describe('limits modal', () => {
    it('seeds the form from existing limits and opens', () => {
      const c = make();
      c.limits = limit;
      c.openLimits();
      expect(c.showLimitsModal).toBe(true);
      expect(c.limitsForm).toEqual({
        dailyPurchaseLimit: 100, dailyWithdrawalLimit: 50, perTxnLimit: 25, monthlyLimit: 1000,
      });
    });

    it('saveLimits updates and closes', () => {
      const c = make();
      c.openLimits();
      c.limitsForm = { dailyPurchaseLimit: 999, dailyWithdrawalLimit: 1, perTxnLimit: 1, monthlyLimit: 1 };
      c.saveLimits();
      expect(svc.updateCardLimits).toHaveBeenCalledWith('card-1', c.limitsForm);
      expect(c.limits?.dailyPurchaseLimit).toBe(999);
      expect(c.showLimitsModal).toBe(false);
    });

    it('saveLimits is a no-op without a loaded card', () => {
      const c = make();
      c.card = null;
      c.saveLimits();
      expect(svc.updateCardLimits).not.toHaveBeenCalled();
    });
  });

  describe('command confirm flow', () => {
    it('confirmAction stages the command and opens the dialog', () => {
      const c = make();
      c.confirmAction('block');
      expect(c.confirmCommand).toBe('block');
      expect(c.showBlockConfirm).toBe(true);
    });

    it('executeCommand applies the command and closes the dialog', () => {
      const c = make();
      c.confirmAction('block');
      c.executeCommand();
      expect(svc.commandCard).toHaveBeenCalledWith('card-1', 'block');
      expect(c.card?.status).toBe('BLOCKED');
      expect(c.showBlockConfirm).toBe(false);
    });
  });

  it('statusVariant and rcVariant map correctly', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('CANCELLED')).toBe('neutral');
    expect(c.rcVariant('00')).toBe('success');
    expect(c.rcVariant('05')).toBe('error');
  });
});
