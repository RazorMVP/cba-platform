import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { AdminService, MakerCheckerEntry, MakerCheckerStatus } from './admin.service';

@Component({
  selector: 'app-maker-checker',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './maker-checker.html',
  styleUrl: './maker-checker.scss',
})
export class MakerCheckerComponent implements OnInit {
  private readonly svc = inject(AdminService);

  entries: MakerCheckerEntry[] = [];
  loading  = true;
  error    = '';

  statusFilter: MakerCheckerStatus | '' = 'PENDING';
  readonly statuses: Array<MakerCheckerStatus | ''> = ['', 'PENDING', 'APPROVED', 'REJECTED'];

  // ── Confirm modal ──────────────────────────────────────────────────────────
  activeModal:  'approve' | 'reject' | null = null;
  targetEntry:  MakerCheckerEntry | null = null;
  modalWorking  = false;
  modalError    = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.svc.listMakerChecker(this.statusFilter || undefined).subscribe({
      next: list => { this.entries = list; this.loading = false; },
      error: () => { this.error = 'Failed to load entries.'; this.loading = false; },
    });
  }

  openApprove(e: MakerCheckerEntry): void {
    this.activeModal = 'approve'; this.targetEntry = e;
    this.modalWorking = false; this.modalError = '';
  }

  openReject(e: MakerCheckerEntry): void {
    this.activeModal = 'reject'; this.targetEntry = e;
    this.modalWorking = false; this.modalError = '';
  }

  confirm(): void {
    if (!this.targetEntry) return;
    this.modalWorking = true;
    const call = this.activeModal === 'approve'
      ? this.svc.approveMakerChecker(this.targetEntry.id)
      : this.svc.rejectMakerChecker(this.targetEntry.id);
    call.subscribe({
      next: updated => {
        this.entries = this.entries.map(e => e.id === updated.id ? updated : e);
        this.modalWorking = false; this.activeModal = null; this.targetEntry = null;
      },
      error: () => { this.modalError = 'Action failed.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) { this.activeModal = null; this.targetEntry = null; } }

  statusVariant(s: MakerCheckerStatus): 'warning' | 'success' | 'neutral' {
    return s === 'PENDING' ? 'warning' : s === 'APPROVED' ? 'success' : 'neutral';
  }
}
