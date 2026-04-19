import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AdminService, ComplianceRow, FailedLoginRow, UserActivityRow, DataAccessRow,
} from './admin.service';

type Tab = 'audit' | 'failed-logins' | 'user-activity' | 'data-access';

@Component({
  selector: 'app-compliance-report',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './compliance-report.html',
  styleUrl: './compliance-report.scss',
})
export class ComplianceReportComponent implements OnInit {
  private readonly svc = inject(AdminService);

  activeTab: Tab = 'audit';
  days = 30;
  entityType = 'LOAN';

  // Audit summary
  auditRows:    ComplianceRow[]     = [];
  auditLoading  = false;
  auditLoaded   = false;
  auditError    = '';

  // Failed logins
  failedRows:   FailedLoginRow[]    = [];
  failedLoading = false;
  failedLoaded  = false;
  failedError   = '';

  // User activity
  activityRows: UserActivityRow[]   = [];
  actLoading    = false;
  actLoaded     = false;
  actError      = '';

  // Data access
  accessRows:   DataAccessRow[]     = [];
  accessLoading = false;
  accessLoaded  = false;
  accessError   = '';

  ngOnInit(): void { this.loadAudit(); }

  switchTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === 'audit'         && !this.auditLoaded)   this.loadAudit();
    if (tab === 'failed-logins' && !this.failedLoaded)  this.loadFailed();
    if (tab === 'user-activity' && !this.actLoaded)     this.loadActivity();
    if (tab === 'data-access'   && !this.accessLoaded)  this.loadAccess();
  }

  reload(): void {
    this.auditLoaded = this.failedLoaded = this.actLoaded = this.accessLoaded = false;
    switch (this.activeTab) {
      case 'audit':         this.loadAudit();    break;
      case 'failed-logins': this.loadFailed();   break;
      case 'user-activity': this.loadActivity(); break;
      case 'data-access':   this.loadAccess();   break;
    }
  }

  loadAudit(): void {
    this.auditLoading = true;
    this.svc.complianceAuditSummary(this.days).subscribe({
      next:  r => { this.auditRows = r; this.auditLoaded = true; this.auditLoading = false; },
      error: () => { this.auditError = 'Failed to load.'; this.auditLoading = false; },
    });
  }

  loadFailed(): void {
    this.failedLoading = true;
    this.svc.complianceFailedLogins(this.days).subscribe({
      next:  r => { this.failedRows = r; this.failedLoaded = true; this.failedLoading = false; },
      error: () => { this.failedError = 'Failed to load.'; this.failedLoading = false; },
    });
  }

  loadActivity(): void {
    this.actLoading = true;
    this.svc.complianceUserActivity(this.days).subscribe({
      next:  r => { this.activityRows = r; this.actLoaded = true; this.actLoading = false; },
      error: () => { this.actError = 'Failed to load.'; this.actLoading = false; },
    });
  }

  loadAccess(): void {
    this.accessLoading = true;
    this.svc.complianceDataAccess(this.days, this.entityType).subscribe({
      next:  r => { this.accessRows = r; this.accessLoaded = true; this.accessLoading = false; },
      error: () => { this.accessError = 'Failed to load.'; this.accessLoading = false; },
    });
  }
}
