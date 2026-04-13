import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { AccountService, Account, Transaction } from '../account.service';
import { PageResponse } from '../../../../core/models/api-response.model';

type ActiveTab = 'overview' | 'transactions';
type ModalType = 'freeze' | 'unfreeze' | 'close' | 'deposit' | 'withdraw' | 'statement' | null;

@Component({
  selector: 'app-account-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './account-detail.html',
  styleUrl: './account-detail.scss',
})
export class AccountDetailComponent implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc    = inject(AccountService);

  account: Account | null = null;
  loading = true;
  error   = '';

  activeTab: ActiveTab = 'overview';

  // Transactions tab (lazy-loaded)
  txns: Transaction[] = [];
  txnPage       = 0;
  txnTotal      = 0;
  txnLoading    = false;
  txnLoaded     = false;
  readonly txnPageSize = 20;

  // Modal state
  activeModal: ModalType = null;
  modalWorking = false;
  modalError   = '';

  // Teller action form
  tellerAmount      = 0;
  tellerDescription = '';

  // Statement
  stmtFrom    = '';
  stmtTo      = '';
  stmtData: Record<string, unknown> | null = null;
  stmtLoading = false;
  stmtError   = '';

  readonly typeLabels: Record<string, string> = {
    SAVINGS: 'Savings', CHECKING: 'Checking', FIXED_DEPOSIT: 'Fixed Deposit',
  };
  readonly typeIcons: Record<string, string> = {
    SAVINGS: 'savings', CHECKING: 'account_balance_wallet', FIXED_DEPOSIT: 'lock_clock',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.get(id).subscribe({
      next:  a  => { this.account = a; this.loading = false; },
      error: () => { this.error = 'Account not found.'; this.loading = false; },
    });
  }

  // ── Tab navigation ──────────────────────────────────────────────────────────

  setTab(tab: ActiveTab): void {
    this.activeTab = tab;
    if (tab === 'transactions' && !this.txnLoaded) {
      this.loadTxns();
    }
  }

  // ── Transactions ────────────────────────────────────────────────────────────

  loadTxns(): void {
    if (!this.account) return;
    this.txnLoading = true;
    this.svc.getTransactions(this.account.id, this.txnPage, this.txnPageSize).subscribe({
      next: (p: PageResponse<Transaction>) => {
        this.txns      = p.content;
        this.txnTotal  = p.totalElements;
        this.txnLoaded = true;
        this.txnLoading = false;
      },
      error: () => { this.txnLoading = false; },
    });
  }

  prevTxnPage(): void {
    if (this.txnPage > 0) { this.txnPage--; this.txnLoaded = false; this.loadTxns(); }
  }

  nextTxnPage(): void {
    if ((this.txnPage + 1) * this.txnPageSize < this.txnTotal) {
      this.txnPage++; this.txnLoaded = false; this.loadTxns();
    }
  }

  get txnTotalPages(): number { return Math.max(1, Math.ceil(this.txnTotal / this.txnPageSize)); }
  get txnStartRow():   number { return this.txnTotal === 0 ? 0 : this.txnPage * this.txnPageSize + 1; }
  get txnEndRow():     number { return Math.min((this.txnPage + 1) * this.txnPageSize, this.txnTotal); }

  // ── Modal helpers ──────────────────────────────────────────────────────────

  openModal(type: ModalType): void {
    this.activeModal      = type;
    this.modalWorking     = false;
    this.modalError       = '';
    this.tellerAmount     = 0;
    this.tellerDescription = '';
    if (type === 'statement') {
      this.stmtData    = null;
      this.stmtError   = '';
      this.stmtLoading = false;
    }
  }

  loadStatement(): void {
    if (!this.account || !this.stmtFrom || !this.stmtTo) return;
    this.stmtLoading = true;
    this.stmtData    = null;
    this.stmtError   = '';
    this.svc.getStatement(this.account.id, this.stmtFrom, this.stmtTo).subscribe({
      next:  data => { this.stmtData = data; this.stmtLoading = false; },
      error: ()   => { this.stmtError = 'Failed to generate statement.'; this.stmtLoading = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  // ── Actions ────────────────────────────────────────────────────────────────

  doFreeze(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.freeze(this.account.id).subscribe({
      next: a  => { this.account = a; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Freeze failed.'; this.modalWorking = false; },
    });
  }

  doUnfreeze(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.unfreeze(this.account.id).subscribe({
      next: a  => { this.account = a; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Unfreeze failed.'; this.modalWorking = false; },
    });
  }

  doClose(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.close(this.account.id).subscribe({
      next: () => { this.router.navigate(['..'], { relativeTo: this.route }); },
      error: () => { this.modalError = 'Close failed. Ensure balance is zero.'; this.modalWorking = false; },
    });
  }

  doTellerAction(): void {
    if (!this.account || this.tellerAmount <= 0) return;
    this.modalWorking = true;
    const req$ = this.activeModal === 'deposit'
      ? this.svc.deposit(this.account.id, this.tellerAmount, this.tellerDescription || undefined)
      : this.svc.withdraw(this.account.id, this.tellerAmount, this.tellerDescription || undefined);

    req$.subscribe({
      next: () => {
        this.modalWorking = false;
        this.activeModal  = null;
        // Reload account balance and invalidate transaction cache
        this.txnLoaded = false;
        this.svc.get(this.account!.id).subscribe({ next: a => { this.account = a; } });
        if (this.activeTab === 'transactions') { this.txnPage = 0; this.loadTxns(); }
      },
      error: () => { this.modalError = 'Transaction failed.'; this.modalWorking = false; },
    });
  }

  // ── Display helpers ────────────────────────────────────────────────────────

  statusVariant(s: Account['status']): 'success' | 'warning' | 'error' | 'neutral' {
    return s === 'ACTIVE' ? 'success' : s === 'DORMANT' ? 'warning' : s === 'FROZEN' ? 'error' : 'neutral';
  }

  txnClass(t: Transaction): string {
    return t.transactionType === 'CREDIT' ? 'amount--credit' : 'amount--debit';
  }

  txnSign(t: Transaction): string {
    return t.transactionType === 'CREDIT' ? '+' : '−';
  }
}
