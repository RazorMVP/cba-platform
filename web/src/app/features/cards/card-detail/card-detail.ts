import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CardsService, Card, CardBalance, CardLimit, AuthorizationLog, UpdateLimitsRequest } from '../cards.service';
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
    this.svc.getCard(id).subscribe({ next: c => { this.card = c; this.loading = false; this.loadBalance(id); this.loadAuths(id); } });
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
}
