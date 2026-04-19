import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { AccountService, Account } from '../accounts/account.service';
import { PaymentService, Payment, TransferRequest, StandingOrderRequest, SOFrequency, ExternalPaymentRequest } from './payment.service';
import { PageResponse } from '../../../core/models/api-response.model';

type ModalType = 'transfer' | 'standing-order' | 'external' | null;
type TransferStep = 1 | 2 | 3;   // 1=accounts, 2=amount+desc, 3=confirm

@Component({
  selector: 'app-payments-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './payments-list.html',
  styleUrl: './payments-list.scss',
})
export class PaymentsListComponent implements OnInit, OnDestroy {
  private readonly paymentSvc = inject(PaymentService);
  private readonly accountSvc = inject(AccountService);
  private readonly destroy$   = new Subject<void>();
  private readonly acctSearch$ = new Subject<string>();

  // ── Account context picker ────────────────────────────────────────────────
  accountQuery  = '';
  accountResults: Account[] = [];
  accountSearching = false;
  selectedAccount: Account | null = null;

  // ── Payments table ────────────────────────────────────────────────────────
  payments: Payment[] = [];
  page      = 0;
  total     = 0;
  loading   = false;
  readonly pageSize = 20;

  // ── Status filter ─────────────────────────────────────────────────────────
  statusFilter: string = '';
  readonly statuses = ['', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED'];

  // ── Modal state ────────────────────────────────────────────────────────────
  activeModal: ModalType = null;
  modalWorking = false;
  modalError   = '';

  // ── Transfer wizard ────────────────────────────────────────────────────────
  transferStep: TransferStep = 1;
  srcAccount:   Account | null = null;
  dstAccount:   Account | null = null;
  srcQuery = '';
  dstQuery = '';
  srcResults: Account[] = [];
  dstResults: Account[] = [];
  srcSearching = false;
  dstSearching = false;
  private readonly srcSearch$ = new Subject<string>();
  private readonly dstSearch$ = new Subject<string>();

  transferAmount      = 0;
  transferDescription = '';

  // ── Standing Order wizard ──────────────────────────────────────────────────
  soSrcAccount:   Account | null = null;
  soDstAccount:   Account | null = null;
  soSrcQuery = '';
  soDstQuery = '';
  soSrcResults: Account[] = [];
  soDstResults: Account[] = [];
  soSrcSearching = false;
  soDstSearching = false;
  private readonly soSrcSearch$ = new Subject<string>();
  private readonly soDstSearch$ = new Subject<string>();

  soAmount      = 0;
  soFrequency: SOFrequency = 'MONTHLY';
  soStartDate   = '';
  soEndDate     = '';
  soDescription = '';
  readonly frequencies: SOFrequency[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'];

  readonly typeLabels: Record<string, string> = {
    INTERNAL_TRANSFER: 'Internal Transfer',
    EXTERNAL_PAYMENT:  'External Payment',
    STANDING_ORDER:    'Standing Order',
    BILL_PAYMENT:      'Bill Payment',
  };
  readonly typeIcons: Record<string, string> = {
    INTERNAL_TRANSFER: 'swap_horiz',
    EXTERNAL_PAYMENT:  'public',
    STANDING_ORDER:    'repeat',
    BILL_PAYMENT:      'receipt',
  };

  ngOnInit(): void {
    // Account context search (debounced)
    this.acctSearch$.pipe(
      debounceTime(300), distinctUntilChanged(),
      switchMap(() => {
        this.accountSearching = true;
        return this.accountSvc.list(0, 10);
      }),
      takeUntil(this.destroy$),
    ).subscribe({
      next: (p: PageResponse<Account>) => {
        this.accountResults  = p.content.filter(a =>
          a.accountNumber.toLowerCase().includes(this.accountQuery.toLowerCase()) ||
          (a.customerName ?? '').toLowerCase().includes(this.accountQuery.toLowerCase())
        );
        this.accountSearching = false;
      },
      error: () => { this.accountSearching = false; },
    });

    // Transfer source/destination search streams
    this.srcSearch$.pipe(debounceTime(250), distinctUntilChanged(),
      switchMap(() => { this.srcSearching = true; return this.accountSvc.list(0, 8); }),
      takeUntil(this.destroy$),
    ).subscribe({ next: p => { this.srcResults = p.content.filter(a => this.matchAcct(a, this.srcQuery)); this.srcSearching = false; },
                  error: () => { this.srcSearching = false; } });

    this.dstSearch$.pipe(debounceTime(250), distinctUntilChanged(),
      switchMap(() => { this.dstSearching = true; return this.accountSvc.list(0, 8); }),
      takeUntil(this.destroy$),
    ).subscribe({ next: p => { this.dstResults = p.content.filter(a => this.matchAcct(a, this.dstQuery)); this.dstSearching = false; },
                  error: () => { this.dstSearching = false; } });

    // External payment source search stream
    this.extSrcSearch$.pipe(debounceTime(250), distinctUntilChanged(),
      switchMap(() => { this.extSrcSearching = true; return this.accountSvc.list(0, 8); }),
      takeUntil(this.destroy$),
    ).subscribe({ next: p => { this.extSrcResults = p.content.filter(a => this.matchAcct(a, this.extSrcQuery)); this.extSrcSearching = false; },
                  error: () => { this.extSrcSearching = false; } });

    // Standing Order source/destination search streams
    this.soSrcSearch$.pipe(debounceTime(250), distinctUntilChanged(),
      switchMap(() => { this.soSrcSearching = true; return this.accountSvc.list(0, 8); }),
      takeUntil(this.destroy$),
    ).subscribe({ next: p => { this.soSrcResults = p.content.filter(a => this.matchAcct(a, this.soSrcQuery)); this.soSrcSearching = false; },
                  error: () => { this.soSrcSearching = false; } });

    this.soDstSearch$.pipe(debounceTime(250), distinctUntilChanged(),
      switchMap(() => { this.soDstSearching = true; return this.accountSvc.list(0, 8); }),
      takeUntil(this.destroy$),
    ).subscribe({ next: p => { this.soDstResults = p.content.filter(a => this.matchAcct(a, this.soDstQuery)); this.soDstSearching = false; },
                  error: () => { this.soDstSearching = false; } });
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  // ── Account context ────────────────────────────────────────────────────────

  onAccountQueryChange(): void { if (this.accountQuery.length >= 2) this.acctSearch$.next(this.accountQuery); }

  selectContextAccount(a: Account): void {
    this.selectedAccount  = a;
    this.accountQuery     = a.accountNumber;
    this.accountResults   = [];
    this.page             = 0;
    this.loadPayments();
  }

  clearContext(): void {
    this.selectedAccount = null;
    this.accountQuery    = '';
    this.accountResults  = [];
    this.payments        = [];
    this.total           = 0;
  }

  loadPayments(): void {
    if (!this.selectedAccount) return;
    this.loading = true;
    this.paymentSvc.getAccountPayments(this.selectedAccount.id, this.page, this.pageSize).subscribe({
      next: (p: PageResponse<Payment>) => {
        this.payments = this.filterPayments(p.content);
        this.total    = p.totalElements;
        this.loading  = false;
      },
      error: () => { this.loading = false; },
    });
  }

  filterPayments(payments: Payment[]): Payment[] {
    if (!this.statusFilter) return payments;
    return payments.filter(p => p.status === this.statusFilter);
  }

  applyStatusFilter(): void { if (this.selectedAccount) { this.page = 0; this.loadPayments(); } }

  prevPage(): void { if (this.page > 0) { this.page--; this.loadPayments(); } }
  nextPage(): void { if ((this.page + 1) * this.pageSize < this.total) { this.page++; this.loadPayments(); } }

  get totalPages(): number { return Math.max(1, Math.ceil(this.total / this.pageSize)); }
  get startRow():   number { return this.total === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.total); }

  // ── Transfer wizard ────────────────────────────────────────────────────────

  openTransferModal(): void {
    this.activeModal        = 'transfer';
    this.transferStep       = 1;
    this.srcAccount         = this.selectedAccount ?? null;
    this.dstAccount         = null;
    this.srcQuery           = this.selectedAccount?.accountNumber ?? '';
    this.dstQuery           = '';
    this.srcResults         = [];
    this.dstResults         = [];
    this.transferAmount     = 0;
    this.transferDescription = '';
    this.modalWorking       = false;
    this.modalError         = '';
  }

  onSrcQueryChange(): void { this.srcSearch$.next(this.srcQuery); }
  onDstQueryChange(): void { this.dstSearch$.next(this.dstQuery); }

  selectSrc(a: Account): void { this.srcAccount = a; this.srcQuery = a.accountNumber; this.srcResults = []; }
  selectDst(a: Account): void { this.dstAccount = a; this.dstQuery = a.accountNumber; this.dstResults = []; }

  get transferStep1Valid(): boolean { return !!this.srcAccount && !!this.dstAccount && this.srcAccount.id !== this.dstAccount.id; }
  get transferStep2Valid(): boolean { return this.transferAmount > 0; }

  nextStep(): void { if (this.transferStep < 3) this.transferStep = (this.transferStep + 1) as TransferStep; }
  prevStep(): void { if (this.transferStep > 1) this.transferStep = (this.transferStep - 1) as TransferStep; }

  submitTransfer(): void {
    if (!this.srcAccount || !this.dstAccount || this.transferAmount <= 0) return;
    this.modalWorking = true;
    this.modalError   = '';
    const req: TransferRequest = {
      sourceAccountId:      this.srcAccount.id,
      destinationAccountId: this.dstAccount.id,
      amount:               this.transferAmount,
      description:          this.transferDescription || undefined,
    };
    this.paymentSvc.transfer(req).subscribe({
      next: p => {
        this.modalWorking = false;
        this.activeModal  = null;
        // If this transfer involves the selected context account, refresh payments
        if (this.selectedAccount && (p.sourceAccountId === this.selectedAccount.id || p.destinationAccountId === this.selectedAccount.id)) {
          this.page = 0;
          this.loadPayments();
        }
      },
      error: () => { this.modalError = 'Transfer failed. Check account balances and try again.'; this.modalWorking = false; },
    });
  }

  // ── Standing Order wizard ──────────────────────────────────────────────────

  openSOModal(): void {
    this.activeModal      = 'standing-order';
    this.soSrcAccount     = this.selectedAccount ?? null;
    this.soDstAccount     = null;
    this.soSrcQuery       = this.selectedAccount?.accountNumber ?? '';
    this.soDstQuery       = '';
    this.soSrcResults     = [];
    this.soDstResults     = [];
    this.soAmount         = 0;
    this.soFrequency      = 'MONTHLY';
    this.soStartDate      = '';
    this.soEndDate        = '';
    this.soDescription    = '';
    this.modalWorking     = false;
    this.modalError       = '';
  }

  onSOSrcQueryChange(): void { this.soSrcSearch$.next(this.soSrcQuery); }
  onSODstQueryChange(): void { this.soDstSearch$.next(this.soDstQuery); }

  selectSOSrc(a: Account): void { this.soSrcAccount = a; this.soSrcQuery = a.accountNumber; this.soSrcResults = []; }
  selectSODst(a: Account): void { this.soDstAccount = a; this.soDstQuery = a.accountNumber; this.soDstResults = []; }

  get soFormValid(): boolean {
    return !!this.soSrcAccount && !!this.soDstAccount &&
           this.soSrcAccount.id !== this.soDstAccount.id &&
           this.soAmount > 0 && !!this.soStartDate;
  }

  submitStandingOrder(): void {
    if (!this.soFormValid) return;
    this.modalWorking = true;
    this.modalError   = '';
    const req: StandingOrderRequest = {
      sourceAccountId:      this.soSrcAccount!.id,
      destinationAccountId: this.soDstAccount!.id,
      amount:               this.soAmount,
      frequency:            this.soFrequency,
      startDate:            this.soStartDate,
      endDate:              this.soEndDate || undefined,
      description:          this.soDescription || undefined,
    };
    this.paymentSvc.createStandingOrder(req).subscribe({
      next: () => { this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Could not create standing order. Please check the details.'; this.modalWorking = false; },
    });
  }

  // ── External Payment modal ─────────────────────────────────────────────────

  extSrcAccount: Account | null = null;
  extSrcQuery   = '';
  extSrcResults: Account[] = [];
  extSrcSearching = false;
  private readonly extSrcSearch$ = new Subject<string>();

  extAmount      = 0;
  extCurrency    = 'USD';
  extNetwork: 'SWIFT' | 'SEPA' | 'ACH' = 'SWIFT';
  extBeneficiaryName    = '';
  extBeneficiaryIban    = '';
  extBeneficiaryBic     = '';
  extBeneficiaryBank    = '';
  extBeneficiaryCountry = '';
  extChargeType: 'SHA' | 'OUR' | 'BEN' = 'SHA';
  extDescription        = '';
  extReference          = '';

  readonly networks  = ['SWIFT', 'SEPA', 'ACH'];
  readonly chargeTypes = ['SHA', 'OUR', 'BEN'];

  openExternalModal(): void {
    this.activeModal        = 'external';
    this.extSrcAccount      = this.selectedAccount ?? null;
    this.extSrcQuery        = this.selectedAccount?.accountNumber ?? '';
    this.extSrcResults      = [];
    this.extAmount          = 0;
    this.extCurrency        = 'USD';
    this.extNetwork         = 'SWIFT';
    this.extBeneficiaryName = '';
    this.extBeneficiaryIban = '';
    this.extBeneficiaryBic  = '';
    this.extBeneficiaryBank = '';
    this.extBeneficiaryCountry = '';
    this.extChargeType      = 'SHA';
    this.extDescription     = '';
    this.extReference       = '';
    this.modalWorking       = false;
    this.modalError         = '';
  }

  onExtSrcQueryChange(): void { this.extSrcSearch$.next(this.extSrcQuery); }
  selectExtSrc(a: Account): void { this.extSrcAccount = a; this.extSrcQuery = a.accountNumber; this.extSrcResults = []; }

  get extFormValid(): boolean {
    return !!this.extSrcAccount && this.extAmount > 0 && !!this.extBeneficiaryName;
  }

  submitExternalPayment(): void {
    if (!this.extFormValid) return;
    this.modalWorking = true;
    this.modalError   = '';
    const req: ExternalPaymentRequest = {
      sourceAccountId:       this.extSrcAccount!.id,
      amount:                this.extAmount,
      currencyCode:          this.extCurrency,
      network:               this.extNetwork,
      beneficiaryName:       this.extBeneficiaryName,
      beneficiaryIban:       this.extBeneficiaryIban   || undefined,
      beneficiaryBic:        this.extBeneficiaryBic    || undefined,
      beneficiaryBankName:   this.extBeneficiaryBank   || undefined,
      beneficiaryCountryCode: this.extBeneficiaryCountry || undefined,
      chargeType:            this.extChargeType,
      description:           this.extDescription       || undefined,
      externalReference:     this.extReference         || undefined,
    };
    this.paymentSvc.initiateExternalPayment(req).subscribe({
      next: p => {
        this.modalWorking = false;
        this.activeModal  = null;
        if (this.selectedAccount && p.sourceAccountId === this.selectedAccount.id) {
          this.page = 0;
          this.loadPayments();
        }
      },
      error: () => { this.modalError = 'External payment failed. Check the details and try again.'; this.modalWorking = false; },
    });
  }

  // ── Modal helpers ──────────────────────────────────────────────────────────

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  // ── Display helpers ────────────────────────────────────────────────────────

  statusVariant(s: string): 'success' | 'warning' | 'error' | 'neutral' {
    if (s === 'COMPLETED') return 'success';
    if (s === 'PENDING' || s === 'PROCESSING') return 'warning';
    if (s === 'FAILED') return 'error';
    return 'neutral';   // REVERSED
  }

  isCredit(p: Payment): boolean {
    return !!this.selectedAccount && p.destinationAccountId === this.selectedAccount.id;
  }

  private matchAcct(a: Account, q: string): boolean {
    const lq = q.toLowerCase();
    return a.accountNumber.toLowerCase().includes(lq) ||
           (a.customerName ?? '').toLowerCase().includes(lq);
  }
}
