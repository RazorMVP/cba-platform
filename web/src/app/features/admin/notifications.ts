import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, NotificationTemplate, NotificationLog, CreateTemplateRequest } from './admin.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';

type Tab = 'templates' | 'history';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './notifications.html',
  styleUrl: './notifications.scss',
})
export class NotificationsComponent implements OnInit {
  private readonly svc = inject(AdminService);

  activeTab: Tab = 'templates';

  // Templates
  templates:    NotificationTemplate[] = [];
  tplLoading    = true;
  tplError      = '';

  // History
  history:      NotificationLog[] = [];
  histLoading   = false;
  histLoaded    = false;
  histError     = '';
  histFilter    = '';

  // Template modal
  showModal    = false;
  editingId: string | null = null;
  working      = false;
  modalError   = '';

  form: CreateTemplateRequest = {
    name: '', eventType: '', deliveryMethod: 'EMAIL', subject: '', body: '',
  };

  // Test modal
  showTest    = false;
  testTplId   = '';
  testRecipient = '';
  testWorking  = false;
  testResult: NotificationLog | null = null;
  testError   = '';

  ngOnInit(): void {
    this.loadTemplates();
  }

  switchTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === 'history' && !this.histLoaded) {
      this.loadHistory();
    }
  }

  // ── Templates ────────────────────────────────────────────────────────────────

  loadTemplates(): void {
    this.tplLoading = true;
    this.svc.listNotificationTemplates().subscribe({
      next:  list => { this.templates = list; this.tplLoading = false; },
      error: ()   => { this.tplError = 'Failed to load templates.'; this.tplLoading = false; },
    });
  }

  openCreate(): void {
    this.editingId  = null;
    this.form       = { name: '', eventType: '', deliveryMethod: 'EMAIL', subject: '', body: '' };
    this.modalError = '';
    this.showModal  = true;
  }

  openEdit(t: NotificationTemplate): void {
    this.editingId  = t.id;
    this.form       = { name: t.name, eventType: t.eventType, deliveryMethod: t.deliveryMethod, subject: t.subject ?? '', body: t.body };
    this.modalError = '';
    this.showModal  = true;
  }

  closeModal(): void { if (!this.working) this.showModal = false; }

  saveTemplate(): void {
    this.working    = true;
    this.modalError = '';
    const req$ = this.editingId
      ? this.svc.updateNotificationTemplate(this.editingId, this.form)
      : this.svc.createNotificationTemplate(this.form);

    req$.subscribe({
      next: () => { this.working = false; this.showModal = false; this.loadTemplates(); },
      error: () => { this.modalError = 'Save failed.'; this.working = false; },
    });
  }

  deactivate(t: NotificationTemplate): void {
    if (!confirm(`Deactivate template "${t.name}"?`)) return;
    this.svc.deactivateNotificationTemplate(t.id).subscribe({ next: () => this.loadTemplates() });
  }

  // ── History ──────────────────────────────────────────────────────────────────

  loadHistory(): void {
    this.histLoading = true;
    const params: Record<string, string> = {};
    if (this.histFilter) params['eventType'] = this.histFilter;
    this.svc.listNotificationHistory(params).subscribe({
      next:  logs => { this.history = logs; this.histLoaded = true; this.histLoading = false; },
      error: ()   => { this.histError = 'Failed to load history.'; this.histLoading = false; },
    });
  }

  applyFilter(): void { this.histLoaded = false; this.loadHistory(); }

  // ── Test notification ─────────────────────────────────────────────────────────

  openTest(t: NotificationTemplate): void {
    this.testTplId    = t.id;
    this.testRecipient = '';
    this.testResult   = null;
    this.testError    = '';
    this.testWorking  = false;
    this.showTest     = true;
  }

  closeTest(): void { if (!this.testWorking) this.showTest = false; }

  sendTest(): void {
    if (!this.testRecipient) return;
    this.testWorking = true;
    this.testError   = '';
    this.svc.sendTestNotification(this.testTplId, this.testRecipient).subscribe({
      next: log => { this.testResult = log; this.testWorking = false; },
      error: ()  => { this.testError = 'Test send failed.'; this.testWorking = false; },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  statusVariant(status: string): 'success' | 'error' | 'warning' | 'neutral' {
    if (status === 'SENT') return 'success';
    if (status === 'FAILED') return 'error';
    return 'neutral';
  }

  methodIcon(method: string): string {
    return method === 'EMAIL' ? 'email' : 'sms';
  }
}
