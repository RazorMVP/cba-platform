import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { OpenBankingService, Consent, ConsentType, ConsentStatus, ConsentFilters } from './open-banking.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-open-banking-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './open-banking-list.html',
  styleUrl: './open-banking-list.scss',
})
export class OpenBankingListComponent implements OnInit {
  private readonly svc   = inject(OpenBankingService);
  private readonly route = inject(ActivatedRoute);

  consents: Consent[] = [];
  loading   = true;
  error     = '';

  typeFilter: ConsentType | ''     = '';
  statusFilter: ConsentStatus | '' = '';
  /** Set from the URL (`?tppClientId=` from the TPP registry, `?customerId=`); shown as a removable chip. */
  tppClientId = '';
  customerId  = '';

  page = 0;
  readonly size = 20;
  totalElements = 0;
  totalPages = 0;

  readonly types:    Array<ConsentType | ''>   = ['', 'AISP', 'PISP', 'CBPII'];
  readonly statuses: Array<ConsentStatus | ''> =
    ['', 'AWAITING_AUTHORISATION', 'AUTHORISED', 'CONSUMED', 'REVOKED', 'EXPIRED'];

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.tppClientId = q.get('tppClientId') ?? '';
    this.customerId  = q.get('customerId') ?? '';
    this.load();
  }

  setType(t: ConsentType | ''): void { this.typeFilter = t; this.page = 0; this.load(); }
  setStatus(): void { this.page = 0; this.load(); }
  clearTpp(): void { this.tppClientId = ''; this.page = 0; this.load(); }
  clearCustomer(): void { this.customerId = ''; this.page = 0; this.load(); }

  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if (this.page + 1 < this.totalPages) { this.page++; this.load(); } }

  load(): void {
    this.loading = true;
    this.error = '';
    const filters: ConsentFilters = {
      type: this.typeFilter || undefined,
      status: this.statusFilter || undefined,
      tppClientId: this.tppClientId || undefined,
      customerId: this.customerId || undefined,
    };
    this.svc.listConsents(filters, this.page, this.size).subscribe({
      next: p => {
        this.consents = p.content;
        this.totalElements = p.totalElements;
        this.totalPages = p.totalPages;
        this.loading = false;
      },
      error: () => { this.error = 'Failed to load consents.'; this.loading = false; },
    });
  }

  statusVariant(s: ConsentStatus): 'warning' | 'success' | 'info' | 'neutral' {
    switch (s) {
      case 'AWAITING_AUTHORISATION': return 'warning';
      case 'AUTHORISED': return 'success';
      case 'CONSUMED': return 'info';
      default: return 'neutral';
    }
  }
}
