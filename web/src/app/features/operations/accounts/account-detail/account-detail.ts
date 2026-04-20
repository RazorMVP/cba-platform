import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { AccountService, Account, Transaction, AccountCreateRequest, AccountHold, AccountHoldRequest, DepositProductSummary, InterestCalculation } from '../account.service';
import { CustomerService, ImageMeta } from '../../customers/customer.service';
import { PageResponse } from '../../../../core/models/api-response.model';
import { environment } from '../../../../../environments/environment';

type ActiveTab = 'overview' | 'transactions' | 'interest' | 'holds' | 'qr';
type ModalType = 'approve' | 'activate' | 'reject' | 'freeze' | 'unfreeze' | 'close' | 'deposit' | 'withdraw' | 'statement' | 'placeHold' | 'releaseHold' | 'reactivate' | 'postInterest' | null;

@Component({
  selector: 'app-account-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './account-detail.html',
  styleUrl: './account-detail.scss',
})
export class AccountDetailComponent implements OnInit {
  private readonly route      = inject(ActivatedRoute);
  private readonly router     = inject(Router);
  private readonly svc        = inject(AccountService);
  private readonly custSvc    = inject(CustomerService);
  private readonly http       = inject(HttpClient);
  private readonly apiBase    = environment.apiBaseUrl;

  account: Account | null = null;
  loading = true;
  error   = '';

  // Creation mode
  isNew = false;
  saving = false;
  saveError = '';
  newForm: AccountCreateRequest = { customerId: '', productId: '', accountType: 'SAVINGS' };

  // Open-account template (product dropdown)
  templateProducts: DepositProductSummary[] = [];
  templateAccountTypes: Account['accountType'][] = ['SAVINGS', 'CHECKING', 'FIXED_DEPOSIT'];
  templateLoading = false;

  // ── Photo (mandatory at account opening) ──────────────────────────────────
  photoMeta: ImageMeta | null = null;
  photoChecking = false;
  photoFile: File | null = null;
  photoPreviewUrl: string | null = null;

  activeTab: ActiveTab = 'overview';

  // Transactions tab (lazy-loaded)
  txns: Transaction[] = [];
  txnPage       = 0;
  txnTotal      = 0;
  txnLoading    = false;
  txnLoaded     = false;
  readonly txnPageSize = 20;

  // Interest tab (lazy-loaded)
  intTxns: Transaction[]  = [];
  intPage       = 0;
  intTotal      = 0;
  intLoading    = false;
  intLoaded     = false;
  readonly intPageSize = 20;

  // Interest actions
  interestPreview: InterestCalculation | null = null;
  interestPreviewLoading = false;

  // Holds tab (lazy-loaded)
  holds: AccountHold[]    = [];
  holdsLoading            = false;
  holdsLoaded             = false;
  holdToRelease: AccountHold | null = null;
  holdForm: AccountHoldRequest = { amount: 0, reason: '' };

  // QR tab (lazy-loaded)
  qrData: any = null;
  qrLoading   = false;
  qrLoaded    = false;
  qrError     = '';

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
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      this.templateLoading = true;
      this.svc.getOpenAccountTemplate().subscribe({
        next: t => {
          this.templateProducts = t.depositProducts;
          this.templateAccountTypes = t.accountTypes;
          this.templateLoading = false;
        },
        error: () => { this.templateLoading = false; },
      });
      return;
    }
    this.svc.get(id).subscribe({
      next:  a  => { this.account = a; this.loading = false; },
      error: () => { this.error = 'Account not found.'; this.loading = false; },
    });
  }

  onProductSelected(productId: string): void {
    const product = this.templateProducts.find(p => p.id === productId);
    if (product?.currencyCode) this.newForm.currencyCode = product.currencyCode;
  }

  onCustomerIdChange(): void {
    const id = this.newForm.customerId?.trim();
    this.photoMeta = null;
    this.photoFile = null;
    this.photoPreviewUrl = null;
    if (!id) return;
    this.photoChecking = true;
    this.custSvc.getImageMeta(id).subscribe({
      next:  meta => { this.photoMeta = meta; this.photoChecking = false; },
      error: ()   => { this.photoMeta = { hasImage: false }; this.photoChecking = false; },
    });
  }

  get photoReady(): boolean {
    // Passes if the customer already has an image OR a new file has been selected
    return (this.photoMeta?.hasImage === true) || this.photoFile !== null;
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.photoFile = file;
    const reader = new FileReader();
    reader.onload = () => this.photoPreviewUrl = reader.result as string;
    reader.readAsDataURL(file);
  }

  clearPhoto(): void {
    this.photoFile = null;
    this.photoPreviewUrl = null;
  }

  submitCreate(): void {
    if (!this.newForm.customerId || !this.newForm.productId) return;
    if (!this.photoReady) {
      this.saveError = 'A customer photo is required before opening an account.';
      return;
    }
    this.saving = true;
    this.saveError = '';

    const doCreate = () => this.svc.create(this.newForm).subscribe({
      next:  a  => this.router.navigate(['..', a.id], { relativeTo: this.route }),
      error: () => { this.saveError = 'Failed to open account. Please try again.'; this.saving = false; },
    });

    if (this.photoFile && !this.photoMeta?.hasImage) {
      // Upload first, then create account
      this.custSvc.uploadImage(this.newForm.customerId, this.photoFile).subscribe({
        next:  () => doCreate(),
        error: () => { this.saveError = 'Photo upload failed. Please try again.'; this.saving = false; },
      });
    } else {
      doCreate();
    }
  }

  // ── Tab navigation ──────────────────────────────────────────────────────────

  setTab(tab: ActiveTab): void {
    this.activeTab = tab;
    if (tab === 'transactions' && !this.txnLoaded) this.loadTxns();
    if (tab === 'interest'     && !this.intLoaded) this.loadIntTxns();
    if (tab === 'holds' && !this.holdsLoaded)       this.loadHolds();
    if (tab === 'qr'    && !this.qrLoaded)          this.loadQr();
  }

  // ── QR Payment ──────────────────────────────────────────────────────────────

  loadQr(): void {
    if (!this.account) return;
    this.qrLoading = true;
    this.qrError   = '';
    this.http.get<any>(`${this.apiBase}/api/v1/accounts/${this.account.id}/qr`).subscribe({
      next: r  => { this.qrData = r?.data ?? r; this.qrLoaded = true; this.qrLoading = false; },
      error: err => {
        this.qrError = err?.error?.errors?.[0]?.message ?? 'Failed to generate QR code';
        this.qrLoading = false;
      },
    });
  }

  refreshQr(): void {
    this.qrLoaded = false;
    this.qrData   = null;
    this.loadQr();
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

  // ── Interest ────────────────────────────────────────────────────────────────

  loadIntTxns(): void {
    if (!this.account) return;
    this.intLoading = true;
    this.svc.getTransactions(this.account.id, this.intPage, this.intPageSize, 'INTEREST_CREDIT').subscribe({
      next: (p: PageResponse<Transaction>) => {
        this.intTxns    = p.content;
        this.intTotal   = p.totalElements;
        this.intLoaded  = true;
        this.intLoading = false;
      },
      error: () => { this.intLoading = false; },
    });
  }

  prevIntPage(): void {
    if (this.intPage > 0) { this.intPage--; this.intLoaded = false; this.loadIntTxns(); }
  }

  nextIntPage(): void {
    if ((this.intPage + 1) * this.intPageSize < this.intTotal) {
      this.intPage++; this.intLoaded = false; this.loadIntTxns();
    }
  }

  get intTotalPages(): number { return Math.max(1, Math.ceil(this.intTotal / this.intPageSize)); }
  get intStartRow():   number { return this.intTotal === 0 ? 0 : this.intPage * this.intPageSize + 1; }
  get intEndRow():     number { return Math.min((this.intPage + 1) * this.intPageSize, this.intTotal); }

  // ── Holds ──────────────────────────────────────────────────────────────────

  loadHolds(): void {
    if (!this.account) return;
    this.holdsLoading = true;
    this.svc.getHolds(this.account.id).subscribe({
      next: h  => { this.holds = h; this.holdsLoaded = true; this.holdsLoading = false; },
      error: () => { this.holdsLoading = false; },
    });
  }

  openPlaceHold(): void {
    this.holdForm = { amount: 0, reason: '' };
    this.openModal('placeHold');
  }

  openReleaseHold(hold: AccountHold): void {
    this.holdToRelease = hold;
    this.openModal('releaseHold');
  }

  doPlaceHold(): void {
    if (!this.account || this.holdForm.amount <= 0 || !this.holdForm.reason) return;
    this.modalWorking = true;
    this.svc.placeHold(this.account.id, this.holdForm).subscribe({
      next: () => {
        this.modalWorking = false;
        this.activeModal  = null;
        this.holdsLoaded  = false;
        this.loadHolds();
        this.svc.get(this.account!.id).subscribe({ next: a => { this.account = a; } });
      },
      error: () => { this.modalError = 'Failed to place hold.'; this.modalWorking = false; },
    });
  }

  doReleaseHold(): void {
    if (!this.account || !this.holdToRelease) return;
    this.modalWorking = true;
    this.svc.releaseHold(this.account.id, this.holdToRelease.id).subscribe({
      next: () => {
        this.modalWorking  = false;
        this.activeModal   = null;
        this.holdToRelease = null;
        this.holdsLoaded   = false;
        this.loadHolds();
        this.svc.get(this.account!.id).subscribe({ next: a => { this.account = a; } });
      },
      error: () => { this.modalError = 'Failed to release hold.'; this.modalWorking = false; },
    });
  }

  doReactivate(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.reactivate(this.account.id).subscribe({
      next: a  => { this.account = a; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Reactivation failed.'; this.modalWorking = false; },
    });
  }

  openPostInterestModal(): void {
    if (!this.account) return;
    this.interestPreview = null;
    this.interestPreviewLoading = true;
    this.openModal('postInterest');
    this.svc.calculateInterest(this.account.id).subscribe({
      next:  calc => { this.interestPreview = calc; this.interestPreviewLoading = false; },
      error: ()   => { this.interestPreviewLoading = false; this.modalError = 'Could not fetch interest preview.'; },
    });
  }

  doPostInterest(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.postInterest(this.account.id).subscribe({
      next: a => {
        this.account = a;
        this.modalWorking = false;
        this.activeModal = null;
        this.intLoaded = false;
        this.loadIntTxns();
      },
      error: () => { this.modalError = 'Interest posting failed.'; this.modalWorking = false; },
    });
  }

  holdStatusVariant(s: AccountHold['status']): 'success' | 'warning' | 'neutral' {
    if (s === 'ACTIVE')   return 'warning';
    if (s === 'RELEASED') return 'success';
    return 'neutral';
  }

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

  doApprove(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.approve(this.account.id).subscribe({
      next: a  => { this.account = a; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Approval failed.'; this.modalWorking = false; },
    });
  }

  doActivate(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.activate(this.account.id).subscribe({
      next: a  => { this.account = a; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Activation failed.'; this.modalWorking = false; },
    });
  }

  doReject(): void {
    if (!this.account) return;
    this.modalWorking = true;
    this.svc.reject(this.account.id).subscribe({
      next: a  => { this.account = a; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Reject failed.'; this.modalWorking = false; },
    });
  }

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

  statusVariant(s: Account['status']): 'success' | 'warning' | 'error' | 'neutral' | 'info' {
    if (s === 'ACTIVE') return 'success';
    if (s === 'APPROVED') return 'info';
    if (s === 'SUBMITTED') return 'warning';
    if (s === 'FROZEN' || s === 'REJECTED') return 'error';
    return 'neutral'; // DORMANT, CLOSED
  }

  /** True when lockinExpiryDate is present and today is on or before that date. */
  get inLockinPeriod(): boolean {
    const expiry = this.account?.lockinExpiryDate;
    if (!expiry) return false;
    return new Date().toISOString().slice(0, 10) <= expiry;
  }

  isCredit(t: Transaction): boolean {
    return t.transactionType.includes('CREDIT');
  }

  txnClass(t: Transaction): string {
    return this.isCredit(t) ? 'amount--credit' : 'amount--debit';
  }

  txnSign(t: Transaction): string {
    return this.isCredit(t) ? '+' : '−';
  }
}
