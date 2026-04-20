import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, ApiKey, IssueApiKeyRequest, IssueApiKeyResponse } from '../cards.service';

const ALL_SCOPES = ['cards:read', 'cards:write', 'analytics:read', 'webhooks:manage', 'disputes:read', 'settlement:read'];

type Tier = 'SANDBOX' | 'BASIC' | 'PRO' | 'ENTERPRISE';

const TIER_LABELS: Record<Tier, string> = {
  SANDBOX: 'Sandbox — 30 req/min',
  BASIC: 'Basic — 100 req/min',
  PRO: 'Pro — 500 req/min',
  ENTERPRISE: 'Enterprise — 2,000 req/min',
};

@Component({
  selector: 'app-api-keys',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './api-keys.html',
  styleUrl: './api-keys.scss',
})
export class ApiKeysComponent implements OnInit {
  private readonly svc = inject(CardsService);

  keys: ApiKey[] = [];
  loading = true;
  showModal = false;
  newKeyResult: IssueApiKeyResponse | null = null;
  copiedKey = false;

  form: IssueApiKeyRequest = { name: '', scopes: [], tier: 'BASIC' };
  readonly allScopes = ALL_SCOPES;
  readonly tiers: Tier[] = ['SANDBOX', 'BASIC', 'PRO', 'ENTERPRISE'];
  readonly tierLabels = TIER_LABELS;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listApiKeys().subscribe({ next: k => { this.keys = k; this.loading = false; }, error: () => { this.loading = false; } });
  }

  openCreate(): void { this.form = { name: '', scopes: [], tier: 'BASIC' }; this.newKeyResult = null; this.showModal = true; }
  closeModal(): void { this.showModal = false; this.newKeyResult = null; if (this.copiedKey) this.load(); this.copiedKey = false; }

  toggleScope(scope: string): void {
    const idx = this.form.scopes.indexOf(scope);
    if (idx >= 0) this.form.scopes.splice(idx, 1); else this.form.scopes.push(scope);
  }

  hasScope(scope: string): boolean { return this.form.scopes.includes(scope); }

  submit(): void {
    this.svc.issueApiKey(this.form).subscribe({ next: r => { this.newKeyResult = r; } });
  }

  copyKey(): void {
    if (!this.newKeyResult) return;
    navigator.clipboard.writeText(this.newKeyResult.key).then(() => { this.copiedKey = true; });
  }

  revoke(id: string): void {
    this.svc.revokeApiKey(id).subscribe({ next: () => this.load() });
  }

  formatDate(d: string | null): string { return d ? new Date(d).toLocaleString() : '—'; }
}
