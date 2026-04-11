import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { OpenBankingService, Consent, ConsentStatus } from '../open-banking.service';

@Component({
  selector: 'app-consent-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent],
  templateUrl: './consent-detail.html',
  styleUrl: './consent-detail.scss',
})
export class ConsentDetailComponent implements OnInit {
  private readonly svc   = inject(OpenBankingService);
  private readonly route = inject(ActivatedRoute);

  consent:  Consent | null = null;
  loading   = true;
  error     = '';

  working     = false;
  actionError = '';

  // ── Confirm modal ──────────────────────────────────────────────────────────
  confirmModal: 'authorise' | 'revoke' | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getConsent(id).subscribe({
      next: c => { this.consent = c; this.loading = false; },
      error: () => { this.error = 'Failed to load consent.'; this.loading = false; },
    });
  }

  openConfirm(action: 'authorise' | 'revoke'): void {
    this.confirmModal = action; this.working = false; this.actionError = '';
  }

  confirm(): void {
    if (!this.consent || !this.confirmModal) return;
    this.working = true;
    const call = this.confirmModal === 'authorise'
      ? this.svc.authoriseConsent(this.consent.id)
      : this.svc.revokeConsent(this.consent.id);
    call.subscribe({
      next: c => { this.consent = c; this.working = false; this.confirmModal = null; },
      error: () => { this.actionError = 'Action failed.'; this.working = false; },
    });
  }

  closeConfirm(): void { if (!this.working) this.confirmModal = null; }

  statusVariant(s: ConsentStatus): 'warning' | 'success' | 'neutral' {
    return s === 'AWAITING_AUTHORISATION' ? 'warning' : s === 'AUTHORISED' ? 'success' : 'neutral';
  }
}
