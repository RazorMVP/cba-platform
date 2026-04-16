import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import {
  TellerService, Teller, Cashier, TellerSession, CashTransaction,
  OpenSessionRequest, CloseSessionRequest, CashTransactionRequest
} from '../teller.service';

type ActiveTab = 'overview' | 'cashiers' | 'sessions';
type ModalType = 'open-session' | 'settle' | 'cash-txn' | 'activate' | 'close-teller' | 'assign-cashier' | null;

@Component({
  selector: 'app-teller-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './teller-detail.html',
  styleUrl: './teller-detail.scss',
})
export class TellerDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly svc   = inject(TellerService);

  teller:   Teller | null = null;
  loading   = true;
  error     = '';
  activeTab: ActiveTab = 'overview';

  // ── Cashiers ──────────────────────────────────────────────────────────────
  cashiers: Cashier[] = [];
  cashiersLoaded = false;

  // ── Sessions ──────────────────────────────────────────────────────────────
  sessions: TellerSession[] = [];
  sessionsLoaded = false;
  selectedSession: TellerSession | null = null;
  sessionTxns: CashTransaction[] = [];
  sessionTxnsLoading = false;

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal: ModalType = null;
  modalWorking = false;
  modalError   = '';

  // Open Session form
  openSessionCashierId = '';
  openSessionBalance   = 0;
  openSessionCurrency  = 'USD';

  // Settle form
  settleActualCash = 0;
  settleNote       = '';

  // Cash transaction form
  cashTxnType: 'CASH_IN' | 'CASH_OUT' = 'CASH_IN';
  cashTxnAmount      = 0;
  cashTxnAccountId   = '';
  cashTxnDescription = '';

  // Assign cashier form
  assignStaffId    = '';
  assignStartDate  = '';
  assignFullDay    = true;
  assignDescription = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.get(id).subscribe({
      next: t => {
        this.teller  = t;
        this.loading = false;
        this.loadCashiers();
      },
      error: () => { this.error = 'Teller not found.'; this.loading = false; },
    });
  }

  setTab(tab: ActiveTab): void {
    this.activeTab = tab;
    if (tab === 'cashiers' && !this.cashiersLoaded) this.loadCashiers();
    if (tab === 'sessions' && !this.sessionsLoaded) this.loadSessions();
  }

  // ── Cashiers ───────────────────────────────────────────────────────────────

  loadCashiers(): void {
    if (!this.teller) return;
    this.svc.getCashiers(this.teller.id).subscribe({
      next: c => { this.cashiers = c; this.cashiersLoaded = true; },
    });
  }

  openAssignCashierModal(): void {
    this.activeModal      = 'assign-cashier';
    this.assignStaffId    = '';
    this.assignStartDate  = new Date().toISOString().slice(0, 10);
    this.assignFullDay    = true;
    this.assignDescription = '';
    this.modalWorking = false;
    this.modalError   = '';
  }

  submitAssignCashier(): void {
    if (!this.teller || !this.assignStaffId || !this.assignStartDate) return;
    this.modalWorking = true;
    this.svc.assignCashier(this.teller.id, {
      staffId:     this.assignStaffId,
      startDate:   this.assignStartDate,
      fullDay:     this.assignFullDay,
      description: this.assignDescription || undefined,
    }).subscribe({
      next: c => {
        this.cashiers = [...this.cashiers, c];
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Failed to assign cashier.'; this.modalWorking = false; },
    });
  }

  // ── Sessions ───────────────────────────────────────────────────────────────

  loadSessions(): void {
    if (!this.teller) return;
    this.svc.getSessions(this.teller.id).subscribe({
      next: s => {
        this.sessions       = s.sort((a, b) => b.sessionDate.localeCompare(a.sessionDate));
        this.sessionsLoaded = true;
      },
    });
  }

  selectSession(s: TellerSession): void {
    if (this.selectedSession?.id === s.id) {
      this.selectedSession = null;
      this.sessionTxns     = [];
      return;
    }
    this.selectedSession    = s;
    this.sessionTxns        = [];
    this.sessionTxnsLoading = true;
    this.svc.getSessionTransactions(this.teller!.id, s.id).subscribe({
      next: t => { this.sessionTxns = t; this.sessionTxnsLoading = false; },
      error: () => { this.sessionTxnsLoading = false; },
    });
  }

  get openSession(): TellerSession | null {
    return this.sessions.find(s => s.status === 'OPEN') ?? null;
  }

  // ── Open Session modal ─────────────────────────────────────────────────────

  openOpenSessionModal(): void {
    this.activeModal          = 'open-session';
    this.openSessionCashierId = this.cashiers.find(c => c.active)?.id ?? (this.cashiers[0]?.id ?? '');
    this.openSessionBalance   = 0;
    this.openSessionCurrency  = 'USD';
    this.modalWorking = false;
    this.modalError   = '';
  }

  submitOpenSession(): void {
    if (!this.teller || !this.openSessionCashierId || this.openSessionBalance < 0) return;
    this.modalWorking = true;
    const req: OpenSessionRequest = {
      openingBalance: this.openSessionBalance,
      currencyCode:   this.openSessionCurrency,
    };
    this.svc.openSession(this.teller.id, this.openSessionCashierId, req).subscribe({
      next: s => {
        this.sessions       = [s, ...this.sessions];
        this.sessionsLoaded = true;
        this.modalWorking   = false;
        this.activeModal    = null;
        this.selectSession(s);
        if (this.activeTab !== 'sessions') this.activeTab = 'sessions';
      },
      error: () => { this.modalError = 'Could not open session. A session may already be open today for this cashier.'; this.modalWorking = false; },
    });
  }

  // ── Cash Transaction modal ─────────────────────────────────────────────────

  openCashTxnModal(type: 'CASH_IN' | 'CASH_OUT'): void {
    this.activeModal        = 'cash-txn';
    this.cashTxnType        = type;
    this.cashTxnAmount      = 0;
    this.cashTxnAccountId   = '';
    this.cashTxnDescription = '';
    this.modalWorking = false;
    this.modalError   = '';
  }

  submitCashTxn(): void {
    if (!this.teller || !this.selectedSession || this.cashTxnAmount <= 0) return;
    this.modalWorking = true;
    const req: CashTransactionRequest = {
      transactionType: this.cashTxnType,
      amount:          this.cashTxnAmount,
      currencyCode:    this.selectedSession.currencyCode,
      accountId:       this.cashTxnAccountId || undefined,
      description:     this.cashTxnDescription || undefined,
    };
    this.svc.recordTransaction(this.teller.id, this.selectedSession.id, req).subscribe({
      next: t => {
        this.sessionTxns  = [...this.sessionTxns, t];
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Transaction failed. Check account balance and session status.'; this.modalWorking = false; },
    });
  }

  // ── Settle modal ───────────────────────────────────────────────────────────

  openSettleModal(): void {
    this.activeModal      = 'settle';
    this.settleActualCash = this.sessionRunningBalance;
    this.settleNote       = '';
    this.modalWorking     = false;
    this.modalError       = '';
  }

  submitSettle(): void {
    if (!this.teller || !this.selectedSession || this.settleActualCash < 0) return;
    this.modalWorking = true;
    const req: CloseSessionRequest = {
      actualCash:     this.settleActualCash,
      settlementNote: this.settleNote || undefined,
    };
    this.svc.closeSession(this.teller.id, this.selectedSession.id, req).subscribe({
      next: s => {
        this.sessions        = this.sessions.map(x => x.id === s.id ? s : x);
        this.selectedSession = s;
        this.modalWorking    = false;
        this.activeModal     = null;
      },
      error: () => { this.modalError = 'Settlement failed.'; this.modalWorking = false; },
    });
  }

  // ── Teller lifecycle ───────────────────────────────────────────────────────

  submitActivate(): void {
    if (!this.teller) return;
    this.modalWorking = true;
    this.svc.activate(this.teller.id).subscribe({
      next: t => { this.teller = t; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Activation failed.'; this.modalWorking = false; },
    });
  }

  submitCloseTeller(): void {
    if (!this.teller) return;
    this.modalWorking = true;
    this.svc.close(this.teller.id).subscribe({
      next: t => { this.teller = t; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Cannot close teller while a session is open.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  // ── Display helpers ────────────────────────────────────────────────────────

  tellerStatusVariant(s: string): 'success' | 'warning' | 'neutral' {
    return s === 'ACTIVE' ? 'success' : s === 'INACTIVE' ? 'warning' : 'neutral';
  }

  sessionStatusVariant(s: string): 'success' | 'neutral' {
    return s === 'OPEN' ? 'success' : 'neutral';
  }

  get sessionRunningBalance(): number {
    if (!this.selectedSession) return 0;
    const opening = this.selectedSession.openingBalance ?? 0;
    return this.sessionTxns.reduce((bal, t) =>
      t.transactionType === 'CASH_IN' ? bal + t.amount : bal - t.amount, opening);
  }

  txnSign(t: CashTransaction): string { return t.transactionType === 'CASH_IN' ? '+' : '−'; }
  txnClass(t: CashTransaction): string { return t.transactionType === 'CASH_IN' ? 'amount--in' : 'amount--out'; }
}
