import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { AccountService, Account, Transaction, AccountCreateRequest } from '../account.service';
import { CustomerService, ImageMeta } from '../../customers/customer.service';
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
  private readonly route      = inject(ActivatedRoute);
  private readonly router     = inject(Router);
  private readonly svc        = inject(AccountService);
  private readonly custSvc    = inject(CustomerService);

  account: Account | null = null;
  loading = true;
  error   = '';

  // Creation mode
  isNew = false;
  saving = false;
  saveError = '';
  newForm: AccountCreateRequest = { customerId: '', productId: '', accountType: 'SAVINGS' };

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
      return;
    }
    this.svc.get(id).subscribe({
      next:  a  => { this.account = a; this.loading = false; },
      error: () => { this.error = 'Account not found.'; this.loading = false; },
    });
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
