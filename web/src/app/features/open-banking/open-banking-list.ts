import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { OpenBankingService, Consent, ConsentType, ConsentStatus } from './open-banking.service';

@Component({
  selector: 'app-open-banking-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './open-banking-list.html',
  styleUrl: './open-banking-list.scss',
})
export class OpenBankingListComponent implements OnInit {
  private readonly svc = inject(OpenBankingService);

  consents: Consent[] = [];
  loading   = true;
  error     = '';

  typeFilter: ConsentType | ''     = '';
  statusFilter: ConsentStatus | '' = '';

  readonly types:    Array<ConsentType | ''>   = ['', 'AISP', 'PISP', 'CBPII'];
  readonly statuses: Array<ConsentStatus | ''> = ['', 'AWAITING_AUTHORISATION', 'AUTHORISED', 'REVOKED', 'EXPIRED'];

  get filtered(): Consent[] {
    return this.consents.filter(c =>
      (!this.typeFilter   || c.consentType === this.typeFilter) &&
      (!this.statusFilter || c.status      === this.statusFilter));
  }

  ngOnInit(): void {
    this.svc.listConsents().subscribe({
      next: list => { this.consents = list; this.loading = false; },
      error: () => { this.error = 'Failed to load consents.'; this.loading = false; },
    });
  }

  typeVariant(t: ConsentType): 'info' | 'success' | 'warning' {
    return t === 'AISP' ? 'info' : t === 'PISP' ? 'success' : 'warning';
  }

  statusVariant(s: ConsentStatus): 'warning' | 'success' | 'neutral' {
    return s === 'AWAITING_AUTHORISATION' ? 'warning' : s === 'AUTHORISED' ? 'success' : 'neutral';
  }
}
