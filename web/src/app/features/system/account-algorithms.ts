import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, TenantAlgorithmConfig, UpdateAlgorithmConfigRequest } from './system.service';
import { AdminService, Tenant } from '../admin/admin.service';

const ACCOUNT_TYPES = ['SAVINGS', 'CHECKING', 'FIXED_DEPOSIT', 'LOAN', 'SHARE'] as const;
const ALGORITHMS    = ['MIFOS', 'NUBAN'] as const;
const VALIDATION_MODES = ['STRICT', 'PARANOID'] as const;

@Component({
  selector: 'app-account-algorithms',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account-algorithms.html',
  styleUrl:    './account-algorithms.scss',
})
export class AccountAlgorithmsComponent implements OnInit {
  private readonly svc       = inject(SystemService);
  private readonly tenantSvc = inject(AdminService);

  tenants:  Tenant[] = [];
  configs:  Map<string, TenantAlgorithmConfig> = new Map();
  loading   = true;
  error     = '';

  // edit state
  editingTenantId = '';
  editForm: UpdateAlgorithmConfigRequest = {
    bankCode:       '',
    validationMode: 'STRICT',
    algorithms:     {},
  };
  editWorking = false;
  editError   = '';

  readonly accountTypes    = ACCOUNT_TYPES;
  readonly algorithms      = ALGORITHMS;
  readonly validationModes = VALIDATION_MODES;

  ngOnInit(): void {
    this.tenantSvc.listTenants().subscribe({
      next: (list: Tenant[]) => {
        this.tenants = list;
        // Load config for each tenant in parallel
        list.forEach((t: Tenant) =>
          this.svc.getAlgorithmConfig(t.id).subscribe({
            next: cfg => this.configs.set(t.id, cfg),
          })
        );
        this.loading = false;
      },
      error: () => { this.error = 'Failed to load tenants.'; this.loading = false; },
    });
  }

  configFor(tenantId: string): TenantAlgorithmConfig | null {
    return this.configs.get(tenantId) ?? null;
  }

  algorithmFor(tenantId: string, type: string): string {
    return this.configs.get(tenantId)?.algorithms?.[type] ?? 'MIFOS';
  }

  startEdit(tenant: Tenant): void {
    const cfg = this.configs.get(tenant.id);
    this.editingTenantId = tenant.id;
    this.editForm = {
      bankCode:       cfg?.bankCode       ?? '',
      validationMode: cfg?.validationMode ?? 'STRICT',
      algorithms:     cfg?.algorithms
        ? { ...cfg.algorithms }
        : Object.fromEntries(ACCOUNT_TYPES.map(t => [t, 'MIFOS'])),
    };
    this.editWorking = false;
    this.editError   = '';
  }

  cancelEdit(): void { this.editingTenantId = ''; }

  setAlgorithm(type: string, algo: string): void {
    this.editForm.algorithms = { ...this.editForm.algorithms, [type]: algo };
  }

  get nubanSelected(): boolean {
    return Object.values(this.editForm.algorithms).includes('NUBAN');
  }

  saveEdit(tenant: Tenant): void {
    if (this.nubanSelected && !this.editForm.bankCode?.trim()) {
      this.editError = 'Bank code is required when NUBAN is selected for any account type.';
      return;
    }
    this.editWorking = true;
    this.svc.updateAlgorithmConfig(tenant.id, this.editForm).subscribe({
      next: updated => {
        this.configs.set(tenant.id, updated);
        this.editingTenantId = '';
        this.editWorking     = false;
      },
      error: err => {
        this.editError   = err?.error?.errors?.[0]?.message ?? 'Failed to save.';
        this.editWorking = false;
      },
    });
  }

  algorithmBadgeClass(algo: string): string {
    return algo === 'NUBAN' ? 'badge-nuban' : 'badge-mifos';
  }
}
