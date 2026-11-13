import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CardDetailComponent } from './card-detail';
import { CardsService, Card, CardBalance, CardLimit, AuthorizationLog, WalletView, WalletMovement } from '../cards.service';
import { AccountService } from '../../operations/accounts/account.service';

type Svc = Record<
  'getCard' | 'getCardBalance' | 'listAuthorizations' | 'updateCardLimits' | 'commandCard' | 'updateControls'
    | 'getWallet' | 'loadWallet' | 'unloadWallet',
  ReturnType<typeof vi.fn>
>;

function card(over: Partial<Card> = {}): Card {
  return {
    id: 'card-1', panPrefix: '457312', panSuffix: '1234', expiryDate: '2612',
    cardType: 'DEBIT', status: 'ACTIVE', virtualFlag: false, customerId: 'cust-1',
    linkedEntityId: 'acc-1', productId: 'p1', productName: 'Classic Debit',
    pinRetryCount: 0, contactlessEnabled: true, cnpEnabled: true, internationalEnabled: true,
    createdAt: '2026-01-01', updatedAt: '2026-01-01', ...over,
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

function walletView(over: Partial<WalletView> = {}): WalletView {
  return { cardId: 'card-1', walletId: 'w1', balance: 75, currencyCode: 'USD', status: 'ACTIVE', movements: [], ...over };
}
function movement(over: Partial<WalletMovement> = {}): WalletMovement {
  return { id: 'm1', cardId: 'card-1', movementType: 'LOAD', reference: 'WLD-1', accountId: 'acc-usd', amount: 25,
    currencyCode: 'USD', status: 'COMPLETED', createdAt: '2026-11-09', ...over };
}

describe('CardDetailComponent', () => {
  let svc: Svc;
  let accounts: { list: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    svc = {
      getCard: vi.fn().mockReturnValue(of(card())),
      getCardBalance: vi.fn().mockReturnValue(of(balance)),
      listAuthorizations: vi.fn().mockReturnValue(of([auth()])),
      updateCardLimits: vi.fn().mockReturnValue(of({ ...limit, dailyPurchaseLimit: 999 })),
      commandCard: vi.fn().mockReturnValue(of(card({ status: 'BLOCKED' }))),
      updateControls: vi.fn().mockReturnValue(of(card({ contactlessEnabled: false }))),
      getWallet: vi.fn().mockReturnValue(of(walletView())),
      loadWallet: vi.fn().mockReturnValue(of(movement())),
      unloadWallet: vi.fn().mockReturnValue(of(movement({ movementType: 'UNLOAD' }))),
    };
    accounts = { list: vi.fn().mockReturnValue(of({ content: [
      { id: 'acc-usd', accountNumber: '001-SAV-1', status: 'ACTIVE', currencyCode: 'USD', balance: 900 },
      { id: 'acc-eur', accountNumber: '001-SAV-2', status: 'ACTIVE', currencyCode: 'EUR', balance: 50 },
      { id: 'acc-frozen', accountNumber: '001-SAV-3', status: 'FROZEN', currencyCode: 'USD', balance: 10 },
    ], totalElements: 3 })) };
    TestBed.configureTestingModule({
      imports: [CardDetailComponent],
      providers: [
        provideRouter([]),
        { provide: CardsService, useValue: svc },
        { provide: AccountService, useValue: accounts },
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

  describe('controls', () => {
    function checkbox(checked: boolean): HTMLInputElement {
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.checked = checked;
      return input;
    }

    it('sends only the switched control and shows the card the server returns', () => {
      const c = make();
      c.setControl('contactlessEnabled', checkbox(false));
      expect(svc.updateControls).toHaveBeenCalledWith('card-1', { contactlessEnabled: false });
      expect(c.card?.contactlessEnabled).toBe(false);
      expect(c.savingControls).toBe(false);
    });

    it('a refusal shows the server message and puts the checkbox back', () => {
      svc.updateControls.mockReturnValue(throwError(() => ({
        error: { errors: [{ code: 'CARD_COUNTRY_UNKNOWN', message: "the card's BIN range has no country" }] },
      })));
      const c = make();
      const input = checkbox(false);
      c.setControl('internationalEnabled', input);
      expect(c.controlsError).toContain('no country');
      expect(input.checked).toBe(true);
      expect(c.card?.internationalEnabled).toBe(true);
    });

    it('renders the three controls on the overview tab', () => {
      const fixture = TestBed.createComponent(CardDetailComponent);
      fixture.detectChanges();
      const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('.controls-card__row input');
      expect(rows.length).toBe(3);
    });
  });
  describe('prepaid wallet (backlog 15c)', () => {
    beforeEach(() => svc.getCard.mockReturnValue(of(card({ cardType: 'PREPAID' }))));

    it('loads the wallet only for a prepaid card and shows it', () => {
      const fixture = TestBed.createComponent(CardDetailComponent);
      fixture.detectChanges();
      expect(svc.getWallet).toHaveBeenCalledWith('card-1');
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Wallet');
    });

    it('does not ask for a wallet for a debit card', () => {
      svc.getCard.mockReturnValue(of(card()));
      make();
      expect(svc.getWallet).not.toHaveBeenCalled();
    });

    it('offers only the customer\'s ACTIVE accounts in the wallet currency', () => {
      const c = make();
      c.openWalletModal('LOAD');
      expect(accounts.list).toHaveBeenCalledWith(0, 100, 'cust-1');
      expect(c.eligibleAccounts.map(a => a.id)).toEqual(['acc-usd']);
    });

    it('loads with the wallet currency and refreshes; a PENDING answer shows the retry notice', () => {
      const c = make();
      c.openWalletModal('LOAD');
      c.walletAccountId = 'acc-usd';
      c.walletAmount = 25;
      svc.loadWallet.mockReturnValue(of(movement({ status: 'PENDING' })));
      c.submitWallet();
      expect(svc.loadWallet).toHaveBeenCalledWith('card-1', { sourceAccountId: 'acc-usd', amount: 25, currencyCode: 'USD' });
      expect(c.walletModal).toBeNull();
      expect(c.walletNotice).toContain('retried');
      expect(svc.getWallet).toHaveBeenCalledTimes(2);
    });

    it('unloads to the chosen account', () => {
      const c = make();
      c.openWalletModal('UNLOAD');
      c.walletAccountId = 'acc-usd';
      c.walletAmount = 10;
      c.submitWallet();
      expect(svc.unloadWallet).toHaveBeenCalledWith('card-1', { destinationAccountId: 'acc-usd', amount: 10 });
    });

    it('explains a refusal in plain words and keeps the modal open', () => {
      svc.loadWallet.mockReturnValue(throwError(() => ({ error: { errors: [{ code: 'INSUFFICIENT_FUNDS', message: 'x' }] } })));
      const c = make();
      c.openWalletModal('LOAD');
      c.walletAccountId = 'acc-usd';
      c.walletAmount = 5000;
      c.submitWallet();
      expect(c.walletError).toContain('enough available balance');
      expect(c.walletModal).toBe('LOAD');
      expect(c.walletWorking).toBe(false);
    });

    it('does nothing without an account or a positive amount', () => {
      const c = make();
      c.openWalletModal('LOAD');
      c.walletAmount = 5;
      c.submitWallet();
      c.walletAccountId = 'acc-usd';
      c.walletAmount = 0;
      c.submitWallet();
      expect(svc.loadWallet).not.toHaveBeenCalled();
    });

    it('a first load (no wallet yet) takes the account\'s currency', () => {
      svc.getWallet.mockReturnValue(of(walletView({ walletId: undefined, currencyCode: undefined, balance: 0 })));
      const c = make();
      c.openWalletModal('LOAD');
      expect(c.eligibleAccounts.map(a => a.id)).toEqual(['acc-usd', 'acc-eur']);
      c.walletAccountId = 'acc-eur';
      c.walletAmount = 20;
      c.submitWallet();
      expect(svc.loadWallet).toHaveBeenCalledWith('card-1', { sourceAccountId: 'acc-eur', amount: 20, currencyCode: 'EUR' });
    });

    it('shows the maximum balance and what can still be loaded today', () => {
      svc.getWallet.mockReturnValue(of(walletView({ currencyCode: 'NGN', balance: 1000,
        limits: { maxBalance: 250000, dailyLoad: 50000, loadedToday: 30000 } })));
      const fixture = TestBed.createComponent(CardDetailComponent);
      fixture.detectChanges();
      expect(fixture.componentInstance.loadableToday()).toBe(20000);
      const text = (fixture.nativeElement as HTMLElement).querySelector('[data-test="wallet-limits"]')?.textContent ?? '';
      expect(text).toContain('Maximum balance 250,000 NGN');
      expect(text).toContain('Can load today 20,000 of 50,000 NGN');
    });

    it('no limits block when the wallet is not limited; loadableToday never goes below 0', () => {
      svc.getWallet.mockReturnValue(of(walletView({ limits: { maxBalance: null, dailyLoad: null, loadedToday: 0 } })));
      const fixture = TestBed.createComponent(CardDetailComponent);
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="wallet-limits"]')).toBeNull();
      expect(fixture.componentInstance.loadableToday()).toBeNull();
      fixture.componentInstance.wallet = walletView({ limits: { maxBalance: null, dailyLoad: 100, loadedToday: 150 } });
      expect(fixture.componentInstance.loadableToday()).toBe(0);
    });

    it('a limit refusal shows the plain words and the server\'s figures', () => {
      svc.loadWallet.mockReturnValue(throwError(() => ({ error: { errors: [{ code: 'DAILY_LOAD_LIMIT',
        message: 'At most 50000 NGN may be loaded a day; 45000 loaded today' }] } })));
      const c = make();
      c.openWalletModal('LOAD');
      c.walletAccountId = 'acc-usd';
      c.walletAmount = 10000;
      c.submitWallet();
      expect(c.walletError).toContain('daily load limit');
      expect(c.walletError).toContain('45000 loaded today');
      expect(c.walletModal).toBe('LOAD');

      svc.loadWallet.mockReturnValue(throwError(() => ({ error: { errors: [{ code: 'WALLET_BALANCE_LIMIT', message: 'm' }] } })));
      c.submitWallet();
      expect(c.walletError).toContain('maximum balance');
    });
  });
});
