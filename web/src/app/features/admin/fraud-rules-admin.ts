import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AdminService, FraudRule } from './admin.service';

@Component({
  selector: 'app-fraud-rules-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fraud-rules-admin.html',
  styleUrl: './fraud-rules-admin.scss',
})
export class FraudRulesAdminComponent implements OnInit, OnDestroy {
  private readonly svc = inject(AdminService);
  private readonly destroy$ = new Subject<void>();

  rules: FraudRule[] = [];
  loading = false;
  saving: string | null = null;

  showEditModal = false;
  editRule: FraudRule | null = null;
  editParams = '';
  editSeverity = '';
  editBlocking = false;

  readonly severityOptions = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  private readonly severityVariants: Record<string, string> = {
    LOW: 'success', MEDIUM: 'warning', HIGH: 'error', CRITICAL: 'critical',
  };

  ngOnInit() { this.loadRules(); }
  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }

  loadRules() {
    this.loading = true;
    this.svc.listFraudRules(0, 50)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: r => { this.rules = r.content; this.loading = false; },
        error: () => { this.loading = false; },
      });
  }

  toggleEnabled(rule: FraudRule) {
    this.saving = rule.id;
    this.svc.updateFraudRule(rule.id, { enabled: !rule.enabled })
      .subscribe({
        next: updated => { Object.assign(rule, updated); this.saving = null; },
        error: () => { this.saving = null; },
      });
  }

  openEdit(rule: FraudRule) {
    this.editRule = rule;
    this.editParams = rule.params ?? '{}';
    this.editSeverity = rule.severity;
    this.editBlocking = rule.blocking;
    this.showEditModal = true;
  }
  confirmEdit() {
    if (!this.editRule) return;
    this.svc.updateFraudRule(this.editRule.id, {
      severity: this.editSeverity,
      blocking: this.editBlocking,
      params: this.editParams,
    }).subscribe({ next: updated => {
      const idx = this.rules.findIndex(r => r.id === updated.id);
      if (idx >= 0) this.rules[idx] = updated;
      this.showEditModal = false;
    }});
  }

  severityChip(s: string) { return this.severityVariants[s] ?? 'neutral'; }
  formatParams(p: string) { try { return JSON.stringify(JSON.parse(p), null, 2); } catch { return p; } }
}
