import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SystemService, AccountNumberFormat, CreateAccountNumberFormatRequest,
  AccountType, PrefixType,
} from './system.service';

@Component({
  selector: 'app-account-number-formats',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account-number-formats.html',
  styleUrl: './account-number-formats.scss',
})
export class AccountNumberFormatsComponent implements OnInit {
  private readonly svc = inject(SystemService);

  formats: AccountNumberFormat[] = [];
  loading = true;
  error   = '';

  activeModal: 'create' | 'edit' | 'delete' | null = null;
  editTarget:   AccountNumberFormat | null = null;
  deleteTarget: AccountNumberFormat | null = null;
  working    = false;
  modalError = '';

  form: CreateAccountNumberFormatRequest = this.blank();

  readonly accountTypes: AccountType[] = ['LOAN', 'SAVINGS', 'CLIENT', 'SHARE'];
  readonly prefixTypes:  PrefixType[]  = [
    'NONE', 'ACCOUNT_TYPE', 'OFFICE_NAME', 'LOAN_PRODUCT_SHORT_NAME', 'CLIENT_NAME',
  ];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listAccountNumberFormats().subscribe({
      next: list => { this.formats = list; this.loading = false; },
      error: () => { this.error = 'Failed to load account number formats.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(f: AccountNumberFormat): void {
    this.form = { accountType: f.accountType, prefixType: f.prefixType };
    this.editTarget = f;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  openDelete(f: AccountNumberFormat): void {
    this.deleteTarget = f;
    this.working = false;
    this.activeModal = 'delete';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateAccountNumberFormat(this.editTarget.id, this.form)
      : this.svc.createAccountNumberFormat(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.working = true;
    this.svc.deleteAccountNumberFormat(this.deleteTarget.id).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  prefixLabel(p: PrefixType): string {
    const map: Record<PrefixType, string> = {
      NONE: 'None',
      ACCOUNT_TYPE: 'Account Type',
      OFFICE_NAME: 'Office Name',
      LOAN_PRODUCT_SHORT_NAME: 'Loan Product Short Name',
      CLIENT_NAME: 'Client Name',
    };
    return map[p] ?? p;
  }

  private blank(): CreateAccountNumberFormatRequest {
    return { accountType: 'SAVINGS', prefixType: 'NONE' };
  }
}
