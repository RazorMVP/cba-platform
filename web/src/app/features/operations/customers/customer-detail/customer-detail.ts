import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import {
  CustomerService, Customer, KycStatus,
  ClientIdentifier, ClientAddress, Beneficiary,
  CustomerCreateRequest, CustomerCommandRequest,
  ImageMeta,
} from '../customer.service';
import { AccountService, Account } from '../../accounts/account.service';
import { LoanService, Loan } from '../../loans/loan.service';
import { environment } from '../../../../../environments/environment';

export type DetailTab =
  | 'overview'
  | 'accounts'
  | 'loans'
  | 'identifiers'
  | 'beneficiaries'
  | 'staff'
  | 'transfer'
  | 'pockets';

const AVATAR_COLORS = ['#3b82f6','#16a34a','#7c3aed','#ea580c','#db2777','#0891b2'];

@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent, CurrencyPipe, DatePipe, TitleCasePipe],
  templateUrl: './customer-detail.html',
  styleUrl: './customer-detail.scss',
})
export class CustomerDetailComponent implements OnInit {
  private readonly route    = inject(ActivatedRoute);
  private readonly router   = inject(Router);
  private readonly custSvc  = inject(CustomerService);
  private readonly accSvc   = inject(AccountService);
  private readonly loanSvc  = inject(LoanService);
  private readonly http     = inject(HttpClient);
  private readonly apiBase  = environment.apiBaseUrl;

  customer: Customer | null = null;
  loading = true;
  error = '';

  // ── Creation mode ───────────────────────────────────────────────────────
  isNew = false;
  saving = false;
  saveError = '';
  newForm: CustomerCreateRequest = { firstName: '', lastName: '', email: '' };

  // ── Photo upload (optional at creation) ─────────────────────────────────
  photoFile: File | null = null;
  photoPreviewUrl: string | null = null;
  photoMeta: ImageMeta | null = null;
  photoDataUrl: string | null = null;

  // ── Tab state ────────────────────────────────────────────────────────────
  activeTab: DetailTab = 'overview';

  readonly tabs: Array<{ id: DetailTab; label: string; icon: string }> = [
    { id: 'overview',      label: 'Overview',      icon: 'person' },
    { id: 'accounts',      label: 'Accounts',      icon: 'account_balance' },
    { id: 'loans',         label: 'Loans',         icon: 'payments' },
    { id: 'identifiers',   label: 'ID & Address',  icon: 'badge' },
    { id: 'beneficiaries', label: 'Beneficiaries', icon: 'group' },
    { id: 'pockets',       label: 'Pockets',       icon: 'folder_open' },
    { id: 'staff',         label: 'Staff',         icon: 'manage_accounts' },
    { id: 'transfer',      label: 'Transfer',      icon: 'swap_horiz' },
  ];

  // ── Accounts tab ─────────────────────────────────────────────────────────
  accounts: Account[] = [];
  accountsLoaded = false;
  accountsLoading = false;

  // ── Loans tab ────────────────────────────────────────────────────────────
  loans: Loan[] = [];
  loansLoaded = false;
  loansLoading = false;

  // ── Identifiers tab ──────────────────────────────────────────────────────
  identifiers: ClientIdentifier[] = [];
  addresses: ClientAddress[] = [];
  identifiersLoaded = false;
  identifiersLoading = false;

  // ── Beneficiaries tab ────────────────────────────────────────────────────
  beneficiaries: Beneficiary[] = [];
  beneficiariesLoaded = false;
  beneficiariesLoading = false;

  // ── Pockets tab ──────────────────────────────────────────────────────────
  pockets: any[] = [];
  pocketsLoaded = false;
  pocketsLoading = false;
  showCreatePocketModal = false;
  pocketForm = { name: '', description: '' };
  pocketSaving = false;
  pocketError = '';

  // ── KYC dropdown (legacy transitions) ───────────────────────────────────
  showKycDropdown = false;
  pendingKycStatus: KycStatus | null = null;
  kycChanging = false;

  readonly kycTransitions: Partial<Record<KycStatus, KycStatus[]>> = {
    PENDING_KYC:         ['ACTIVE'],
    ACTIVE:              ['SUSPENDED', 'CLOSED'],
    SUSPENDED:           ['ACTIVE', 'CLOSED'],
    CLOSED:              [],
    REJECTED:            [],
    WITHDRAWN:           [],
    TRANSFER_IN_PROGRESS: [],
  };

  // ── Command modals ────────────────────────────────────────────────────────
  // Shared command working state
  cmdWorking = false;
  cmdError = '';

  // Reject modal
  showRejectModal = false;
  rejectReason = '';

  // Withdraw modal
  showWithdrawModal = false;
  withdrawReason = '';

  // Close modal
  showCloseModal = false;
  closeReason = '';

  // Reactivate / undo modals (no payload)
  showReactivateConfirm = false;
  showUndoRejectionConfirm = false;
  showUndoWithdrawalConfirm = false;

  // Assign staff modal
  showAssignStaffModal = false;
  assignStaffId = '';

  // Transfer modals
  showProposeTransferModal = false;
  transferDestinationOfficeId = '';
  transferDate = '';
  transferNote = '';

  showAcceptTransferConfirm = false;
  showRejectTransferConfirm = false;
  showWithdrawTransferConfirm = false;

  // Delete confirm
  showDeleteConfirm = false;
  deleteWorking = false;

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      return;
    }
    this.custSvc.get(id).subscribe({
      next: c => {
        this.customer = c;
        this.loading = false;
        this.custSvc.getImageMeta(c.id).subscribe(meta => {
          this.photoMeta = meta;
          if (meta.hasImage) {
            this.custSvc.getImageDataUrl(c.id).subscribe(url => this.photoDataUrl = url);
          }
        });
      },
      error: () => { this.error = 'Customer not found.'; this.loading = false; },
    });
  }

  // ── Creation ──────────────────────────────────────────────────────────────

  submitCreate(): void {
    if (!this.newForm.firstName || !this.newForm.lastName || !this.newForm.email) return;
    this.saving = true;
    this.saveError = '';
    this.custSvc.create(this.newForm).subscribe({
      next: c => {
        if (this.photoFile) {
          // Upload photo then navigate — failure is non-blocking
          this.custSvc.uploadImage(c.id, this.photoFile).subscribe({
            next:  () => this.router.navigate(['..', c.id], { relativeTo: this.route }),
            error: () => this.router.navigate(['..', c.id], { relativeTo: this.route }),
          });
        } else {
          this.router.navigate(['..', c.id], { relativeTo: this.route });
        }
      },
      error: () => { this.saveError = 'Failed to create customer. Please try again.'; this.saving = false; },
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.photoFile = file;
    // Local preview before upload
    const reader = new FileReader();
    reader.onload = () => this.photoPreviewUrl = reader.result as string;
    reader.readAsDataURL(file);
  }

  clearPhoto(): void {
    this.photoFile = null;
    this.photoPreviewUrl = null;
  }

  // ── Tab navigation ────────────────────────────────────────────────────────

  selectTab(tab: DetailTab): void {
    this.activeTab = tab;
    const id = this.customer?.id;
    if (!id) return;

    if (tab === 'accounts' && !this.accountsLoaded) {
      this.accountsLoading = true;
      this.accSvc.list(0, 50, id).subscribe({
        next:  p  => { this.accounts = p.content; this.accountsLoaded = true; this.accountsLoading = false; },
        error: () => { this.accountsLoading = false; },
      });
    }
    if (tab === 'loans' && !this.loansLoaded) {
      this.loansLoading = true;
      this.loanSvc.list(0, 50, undefined, id).subscribe({
        next:  p  => { this.loans = p.content; this.loansLoaded = true; this.loansLoading = false; },
        error: () => { this.loansLoading = false; },
      });
    }
    if (tab === 'identifiers' && !this.identifiersLoaded) {
      this.identifiersLoading = true;
      this.custSvc.getIdentifiers(id).subscribe({ next: list => { this.identifiers = list; } });
      this.custSvc.getAddresses(id).subscribe({
        next:  list => { this.addresses = list; this.identifiersLoaded = true; this.identifiersLoading = false; },
        error: () => { this.identifiersLoading = false; },
      });
    }
    if (tab === 'beneficiaries' && !this.beneficiariesLoaded) {
      this.beneficiariesLoading = true;
      this.custSvc.getBeneficiaries(id).subscribe({
        next:  list => { this.beneficiaries = list; this.beneficiariesLoaded = true; this.beneficiariesLoading = false; },
        error: () => { this.beneficiariesLoading = false; },
      });
    }
    if (tab === 'pockets' && !this.pocketsLoaded) {
      this.loadPockets();
    }
  }

  // ── Pockets ───────────────────────────────────────────────────────────────

  loadPockets(): void {
    const id = this.customer?.id;
    if (!id) return;
    this.pocketsLoading = true;
    this.http.get<any>(`${this.apiBase}/api/v1/pockets?customerId=${id}`).subscribe({
      next: r => {
        this.pockets = r?.data ?? r ?? [];
        this.pocketsLoaded = true;
        this.pocketsLoading = false;
      },
      error: () => { this.pocketsLoading = false; },
    });
  }

  openCreatePocketModal(): void {
    this.pocketForm = { name: '', description: '' };
    this.pocketError = '';
    this.showCreatePocketModal = true;
  }

  savePocket(): void {
    const id = this.customer?.id;
    if (!id || !this.pocketForm.name.trim()) return;
    this.pocketSaving = true;
    this.pocketError = '';
    this.http.post<any>(`${this.apiBase}/api/v1/pockets`, {
      customerId: id,
      name: this.pocketForm.name,
      description: this.pocketForm.description || null,
      accountIds: [],
    }).subscribe({
      next: r => {
        const pocket = r?.data ?? r;
        this.pockets = [pocket, ...this.pockets];
        this.pocketSaving = false;
        this.showCreatePocketModal = false;
      },
      error: err => {
        this.pocketError = err?.error?.errors?.[0]?.message ?? 'Failed to create pocket';
        this.pocketSaving = false;
      },
    });
  }

  closePocket(pocketId: string): void {
    const customerId = this.customer?.id;
    if (!customerId) return;
    this.http.delete<any>(`${this.apiBase}/api/v1/pockets/${pocketId}?customerId=${customerId}`).subscribe({
      next: () => { this.pockets = this.pockets.filter(p => p.id !== pocketId); },
    });
  }

  // ── KYC dropdown (legacy) ─────────────────────────────────────────────────

  confirmKycChange(): void {
    if (!this.pendingKycStatus || !this.customer) return;
    this.kycChanging = true;
    this.custSvc.updateKycStatus(this.customer.id, this.pendingKycStatus).subscribe({
      next: updated => {
        this.customer = updated;
        this.showKycDropdown = false;
        this.pendingKycStatus = null;
        this.kycChanging = false;
      },
      error: () => { this.kycChanging = false; },
    });
  }

  // ── Commands ──────────────────────────────────────────────────────────────

  private runCommand(command: string, payload: CustomerCommandRequest = {}): void {
    if (!this.customer) return;
    this.cmdWorking = true;
    this.cmdError = '';
    this.custSvc.executeCommand(this.customer.id, command, payload).subscribe({
      next: updated => {
        this.customer = updated;
        this.cmdWorking = false;
        this.closeAllModals();
      },
      error: (err) => {
        this.cmdError = err?.error?.errors?.[0]?.message ?? 'Operation failed. Please try again.';
        this.cmdWorking = false;
      },
    });
  }

  rejectCustomer(): void {
    this.runCommand('reject', { reason: this.rejectReason || undefined });
  }

  withdrawCustomer(): void {
    this.runCommand('withdraw', { reason: this.withdrawReason || undefined });
  }

  closeCustomer(): void {
    this.runCommand('close', { reason: this.closeReason || undefined });
  }

  reactivateCustomer(): void    { this.runCommand('reactivate'); }
  undoRejection(): void         { this.runCommand('undoRejection'); }
  undoWithdrawal(): void        { this.runCommand('undoWithdrawal'); }

  assignStaff(): void {
    if (!this.assignStaffId.trim()) return;
    this.runCommand('assignStaff', { staffId: this.assignStaffId.trim() });
  }

  unassignStaff(): void { this.runCommand('unassignStaff'); }

  proposeTransfer(): void {
    if (!this.transferDestinationOfficeId.trim()) return;
    this.runCommand('proposeTransfer', {
      destinationOfficeId: this.transferDestinationOfficeId.trim(),
      transferDate: this.transferDate || undefined,
      transferNote: this.transferNote || undefined,
    });
  }

  acceptTransfer(): void  { this.runCommand('acceptTransfer'); }
  rejectTransfer(): void  { this.runCommand('rejectTransfer'); }
  withdrawTransfer(): void { this.runCommand('withdrawTransfer'); }

  // ── Delete ────────────────────────────────────────────────────────────────

  confirmDelete(): void {
    if (!this.customer) return;
    this.deleteWorking = true;
    this.custSvc.delete(this.customer.id).subscribe({
      next: () => this.router.navigate(['..'], { relativeTo: this.route }),
      error: (err) => {
        this.cmdError = err?.error?.errors?.[0]?.message ?? 'Delete failed.';
        this.deleteWorking = false;
        this.showDeleteConfirm = false;
      },
    });
  }

  // ── Utilities ─────────────────────────────────────────────────────────────

  closeAllModals(): void {
    this.showRejectModal = false;
    this.showWithdrawModal = false;
    this.showCloseModal = false;
    this.showReactivateConfirm = false;
    this.showUndoRejectionConfirm = false;
    this.showUndoWithdrawalConfirm = false;
    this.showAssignStaffModal = false;
    this.showProposeTransferModal = false;
    this.showAcceptTransferConfirm = false;
    this.showRejectTransferConfirm = false;
    this.showWithdrawTransferConfirm = false;
    this.showDeleteConfirm = false;
    this.rejectReason = '';
    this.withdrawReason = '';
    this.closeReason = '';
    this.assignStaffId = '';
    this.transferDestinationOfficeId = '';
    this.transferDate = '';
    this.transferNote = '';
    this.cmdError = '';
  }

  get availableKycTransitions(): KycStatus[] {
    return this.customer ? (this.kycTransitions[this.customer.kycStatus] ?? []) : [];
  }

  get initials(): string {
    if (!this.customer) return '?';
    return `${this.customer.firstName?.[0] ?? ''}${this.customer.lastName?.[0] ?? ''}`.toUpperCase();
  }

  get avatarColor(): string {
    if (!this.customer) return AVATAR_COLORS[0];
    const idx = this.customer.firstName.charCodeAt(0) % AVATAR_COLORS.length;
    return AVATAR_COLORS[idx];
  }

  kycVariant(s: KycStatus): 'success' | 'warning' | 'error' | 'neutral' | 'info' {
    switch (s) {
      case 'ACTIVE':              return 'success';
      case 'PENDING_KYC':         return 'warning';
      case 'SUSPENDED':           return 'error';
      case 'REJECTED':            return 'error';
      case 'WITHDRAWN':           return 'neutral';
      case 'TRANSFER_IN_PROGRESS': return 'info';
      default:                    return 'neutral';
    }
  }

  kycLabel(s: KycStatus): string {
    const map: Record<KycStatus, string> = {
      PENDING_KYC:          'Pending KYC',
      ACTIVE:               'Active',
      SUSPENDED:            'Suspended',
      CLOSED:               'Closed',
      REJECTED:             'Rejected',
      WITHDRAWN:            'Withdrawn',
      TRANSFER_IN_PROGRESS: 'Transfer In Progress',
    };
    return map[s] ?? s;
  }

  accountStatusVariant(s: Account['status']): 'success' | 'warning' | 'error' | 'neutral' {
    return s === 'ACTIVE' ? 'success' : s === 'DORMANT' ? 'warning' : s === 'FROZEN' ? 'error' : 'neutral';
  }

  loanStatusVariant(s: Loan['status']): 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary' {
    const m: Record<string, 'success'|'warning'|'error'|'info'|'neutral'|'primary'> = {
      ACTIVE:'primary', SUBMITTED:'info', UNDER_REVIEW:'warning',
      APPROVED:'success', DISBURSED:'success', IN_ARREARS:'error',
      WRITTEN_OFF:'error', CLOSED_OBLIGATIONS_MET:'neutral',
    };
    return m[s] ?? 'neutral';
  }

  loanStatusLabel(s: Loan['status']): string {
    const m: Record<string, string> = {
      SUBMITTED:'Submitted', UNDER_REVIEW:'Under Review', APPROVED:'Approved',
      DISBURSED:'Disbursed', ACTIVE:'Active', IN_ARREARS:'In Arrears',
      CLOSED_OBLIGATIONS_MET:'Closed', WRITTEN_OFF:'Written Off',
    };
    return m[s] ?? s;
  }
}
