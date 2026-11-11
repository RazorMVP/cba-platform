import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CardsService, Card, CardBalance, CardLimit, AuthorizationLog, UpdateLimitsRequest, WalletView } from '../cards.service';
import { AccountService, Account } from '../../operations/accounts/account.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

type V = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-card-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './card-detail.html',
  styleUrl: './card-detail.scss',
})
export class CardDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly svc   = inject(CardsService);
  private readonly accountsSvc = inject(AccountService);

  card: Card | null = null;
  balance: CardBalance | null = null;
  limits: CardLimit | null = null;
  auths: AuthorizationLog[] = [];
  loading = true;
  activeTab: 'overview' | 'authorizations' | 'limits' = 'overview';

  showLimitsModal = false;
  limitsForm: UpdateLimitsRequest = { dailyPurchaseLimit: 0, dailyWithdrawalLimit: 0, perTxnLimit: 0, monthlyLimit: 0 };

  showBlockConfirm = false;
  confirmCommand = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.load(id);
  }

  load(id: string): void {
    this.loading = true;
    this.svc.getCard(id).subscribe({ next: c => {
      this.card = c; this.loading = false; this.loadBalance(id); this.loadAuths(id);
      if (c.cardType === 'PREPAID') this.loadWallet(id);
    } });
  }

  loadBalance(id: string): void { this.svc.getCardBalance(id).subscribe({ next: b => this.balance = b, error: () => {} }); }
  loadAuths(id: string): void { this.svc.listAuthorizations(id).subscribe({ next: a => this.auths = a, error: () => {} }); }

  openLimits(): void {
    if (this.limits) {
      this.limitsForm = { dailyPurchaseLimit: this.limits.dailyPurchaseLimit, dailyWithdrawalLimit: this.limits.dailyWithdrawalLimit, perTxnLimit: this.limits.perTxnLimit, monthlyLimit: this.limits.monthlyLimit };
    }
    this.showLimitsModal = true;
  }

  saveLimits(): void {
    if (!this.card) return;
    this.svc.updateCardLimits(this.card.id, this.limitsForm).subscribe({ next: l => { this.limits = l; this.showLimitsModal = false; } });
  }

  confirmAction(cmd: string): void { this.confirmCommand = cmd; this.showBlockConfirm = true; }
  executeCommand(): void {
    if (!this.card) return;
    this.svc.commandCard(this.card.id, this.confirmCommand).subscribe({ next: c => { this.card = c; this.showBlockConfirm = false; } });
  }

  statusVariant(s: string): V {
    const m: Record<string, V> = { ACTIVE: 'success', BLOCKED: 'error', EXPIRED: 'neutral', CANCELLED: 'neutral', ORDERED: 'info', PRODUCED: 'info', DISPATCHED: 'warning', ACTIVATION_PENDING: 'warning' };
    return m[s] ?? 'neutral';
  }

  rcVariant(rc: string): V { return rc === '00' ? 'success' : 'error'; }

  controlsError = '';
  savingControls = false;
  /**
   * Turns one cardholder control on or off. On a refusal the checkbox is put back — its [checked] binding
   * didn't change, so Angular wouldn't reset it — and the server's message is shown.
   */
  setControl(key: 'contactlessEnabled' | 'cnpEnabled' | 'internationalEnabled', input: HTMLInputElement): void {
    if (!this.card) return;
    const enabled = input.checked;
    this.controlsError = '';
    this.savingControls = true;
    this.svc.updateControls(this.card.id, { [key]: enabled }).subscribe({
      next: c => { this.card = c; this.savingControls = false; },
      error: (e: { error?: { errors?: { message?: string }[] } }) => {
        input.checked = !enabled;
        this.controlsError = e?.error?.errors?.[0]?.message ?? 'Could not update the control';
        this.savingControls = false;
      },
    });
  }

  // ── Prepaid wallet (backlog 15c) ─────────────────────────────────────────
  wallet: WalletView | null = null;
  walletAccounts: Account[] = [];
  walletModal: 'LOAD' | 'UNLOAD' | null = null;
  walletAccountId = '';
  walletAmount: number | null = null;
  walletCurrency = 'USD';
  walletWorking = false;
  walletError = '';
  walletNotice = '';

  loadWallet(id: string): void {
    this.svc.getWallet(id).subscribe({ next: w => this.wallet = w, error: () => this.wallet = null });
  }

  /** The card customer's ACTIVE accounts, in the wallet's currency once it has one. */
  get eligibleAccounts(): Account[] {
    const ccy = this.wallet?.currencyCode;
    return this.walletAccounts.filter(a => a.status === 'ACTIVE' && (!ccy || a.currencyCode === ccy));
  }

  openWalletModal(kind: 'LOAD' | 'UNLOAD'): void {
    this.walletModal = kind;
    this.walletAccountId = '';
    this.walletAmount = null;
    this.walletError = '';
    this.walletWorking = false;
    if (this.card) {
      this.accountsSvc.list(0, 100, this.card.customerId).subscribe({
        next: p => this.walletAccounts = p.content,
        error: () => { this.walletAccounts = []; this.walletError = 'Could not load the customer\'s accounts.'; },
      });
    }
  }

  closeWalletModal(): void { if (!this.walletWorking) this.walletModal = null; }

  submitWallet(): void {
    if (!this.card || !this.walletModal || !this.walletAccountId || !this.walletAmount || this.walletAmount <= 0) return;
    const id = this.card.id;
    const kind = this.walletModal;
    const account = this.walletAccounts.find(a => a.id === this.walletAccountId);
    this.walletWorking = true;
    this.walletError = '';
    const call = kind === 'LOAD'
      ? this.svc.loadWallet(id, { sourceAccountId: this.walletAccountId, amount: this.walletAmount,
          currencyCode: this.wallet?.currencyCode ?? account?.currencyCode })
      : this.svc.unloadWallet(id, { destinationAccountId: this.walletAccountId, amount: this.walletAmount });
    call.subscribe({
      next: m => {
        this.walletWorking = false;
        this.walletModal = null;
        this.walletNotice = m.status === 'PENDING'
          ? `${kind === 'LOAD' ? 'Load' : 'Unload'} sent; the bank hasn't confirmed it yet — it is retried automatically.`
          : '';
        this.loadWallet(id);
        this.loadBalance(id);
      },
      error: (e: { error?: { errors?: { code?: string; message?: string }[] } }) => {
        const err = e?.error?.errors?.[0];
        this.walletError = this.walletErrorText(err?.code) ?? err?.message ?? 'The request failed.';
        this.walletWorking = false;
      },
    });
  }

  private walletErrorText(code?: string): string | null {
    const m: Record<string, string> = {
      INSUFFICIENT_FUNDS: 'The account doesn\'t have enough available balance.',
      INSUFFICIENT_WALLET_BALANCE: 'The wallet doesn\'t hold that much.',
      CURRENCY_MISMATCH: 'The account is in a different currency from the wallet.',
      ACCOUNT_NOT_ACTIVE: 'The account isn\'t active.',
      ACCOUNT_NOT_FOUND: 'That account doesn\'t belong to the card\'s customer.',
      CARD_NOT_LOADABLE: 'A cancelled or expired card can\'t be loaded.',
    };
    return code ? m[code] ?? null : null;
  }

  movementVariant(s: string): V {
    return s === 'COMPLETED' ? 'success' : s === 'FAILED' ? 'error' : 'warning';
  }
}
