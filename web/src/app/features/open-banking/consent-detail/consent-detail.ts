import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { OpenBankingService, Consent, ConsentStatus } from '../open-banking.service';

/**
 * Staff view of one consent. Authorising is the customer's own step (not offered here); staff can
 * revoke an active consent on the customer's behalf, with a reason that is audited.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-consent-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './consent-detail.html',
  styleUrl: './consent-detail.scss',
})
export class ConsentDetailComponent implements OnInit {
  private readonly svc   = inject(OpenBankingService);
  private readonly route = inject(ActivatedRoute);

  consent:  Consent | null = null;
  loading   = true;
  error     = '';

  revokeOpen  = false;
  reason      = '';
  working     = false;
  actionError = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getConsent(id).subscribe({
      next: c => { this.consent = c; this.loading = false; },
      error: () => { this.error = 'Failed to load consent.'; this.loading = false; },
    });
  }

  /** Revocable while the TPP can still use it. */
  get canRevoke(): boolean {
    return !!this.consent && (this.consent.status === 'AUTHORISED' || this.consent.status === 'AWAITING_AUTHORISATION');
  }

  openRevoke(): void { this.revokeOpen = true; this.reason = ''; this.working = false; this.actionError = ''; }

  closeRevoke(): void { if (!this.working) this.revokeOpen = false; }

  revoke(): void {
    if (!this.consent || !this.reason.trim()) return;
    this.working = true;
    this.svc.revokeConsent(this.consent.consentId, this.reason.trim()).subscribe({
      next: c => { this.consent = c; this.working = false; this.revokeOpen = false; },
      error: (e: HttpErrorResponse) => {
        this.actionError = e.error?.errors?.[0]?.message ?? 'Revoke failed.';
        this.working = false;
      },
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
