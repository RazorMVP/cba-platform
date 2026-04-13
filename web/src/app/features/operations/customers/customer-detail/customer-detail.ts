import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import {
  CustomerService, Customer, KycStatus,
  ClientIdentifier, ClientAddress, Beneficiary,
  CustomerCreateRequest,
} from '../customer.service';
import { AccountService, Account } from '../../accounts/account.service';
import { LoanService, Loan } from '../../loans/loan.service';

export type DetailTab = 'overview' | 'accounts' | 'loans' | 'identifiers' | 'beneficiaries';

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

  customer: Customer | null = null;
  loading = true;
  error = '';

  // Creation mode
  isNew = false;
  saving = false;
  saveError = '';
  newForm: CustomerCreateRequest = { firstName: '', lastName: '', email: '' };

  activeTab: DetailTab = 'overview';

  // Accounts tab
  accounts: Account[] = [];
  accountsLoaded = false;
  accountsLoading = false;

  // Loans tab
  loans: Loan[] = [];
  loansLoaded = false;
  loansLoading = false;

  // Identifiers tab
  identifiers: ClientIdentifier[] = [];
  addresses: ClientAddress[] = [];
  identifiersLoaded = false;
  identifiersLoading = false;

  // Beneficiaries tab
  beneficiaries: Beneficiary[] = [];
  beneficiariesLoaded = false;
  beneficiariesLoading = false;

  // KYC change workflow
  showKycDropdown = false;
  pendingKycStatus: KycStatus | null = null;
  kycChanging = false;

  readonly kycTransitions: Record<KycStatus, KycStatus[]> = {
    PENDING_KYC: ['ACTIVE'],
    ACTIVE:      ['SUSPENDED', 'CLOSED'],
    SUSPENDED:   ['ACTIVE', 'CLOSED'],
    CLOSED:      [],
  };

  readonly tabs: Array<{ id: DetailTab; label: string; icon: string }> = [
    { id: 'overview',      label: 'Overview',      icon: 'person' },
    { id: 'accounts',      label: 'Accounts',      icon: 'account_balance' },
    { id: 'loans',         label: 'Loans',         icon: 'payments' },
    { id: 'identifiers',   label: 'ID & Address',  icon: 'badge' },
    { id: 'beneficiaries', label: 'Beneficiaries', icon: 'group' },
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      return;
    }
    this.custSvc.get(id).subscribe({
      next:  c  => { this.customer = c; this.loading = false; },
      error: () => { this.error = 'Customer not found.'; this.loading = false; },
    });
  }

  submitCreate(): void {
    if (!this.newForm.firstName || !this.newForm.lastName || !this.newForm.email) return;
    this.saving = true;
    this.saveError = '';
    this.custSvc.create(this.newForm).subscribe({
      next:  c  => this.router.navigate(['..', c.id], { relativeTo: this.route }),
      error: () => { this.saveError = 'Failed to create customer. Please try again.'; this.saving = false; },
    });
  }

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
  }

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

  kycVariant(s: KycStatus): 'success' | 'warning' | 'error' | 'neutral' {
    return s === 'ACTIVE' ? 'success' : s === 'PENDING_KYC' ? 'warning' : s === 'SUSPENDED' ? 'error' : 'neutral';
  }
  kycLabel(s: KycStatus): string {
    return s === 'PENDING_KYC' ? 'Pending KYC' : s.charAt(0) + s.slice(1).toLowerCase();
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
    const m: Record<string,string> = { SUBMITTED:'Submitted', UNDER_REVIEW:'Under Review', APPROVED:'Approved', DISBURSED:'Disbursed', ACTIVE:'Active', IN_ARREARS:'In Arrears', CLOSED_OBLIGATIONS_MET:'Closed', WRITTEN_OFF:'Written Off' };
    return m[s] ?? s;
  }
}
