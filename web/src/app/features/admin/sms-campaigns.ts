import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { AdminService, SmsCampaign, SmsMessage, CreateSmsCampaignRequest } from './admin.service';
import { PageResponse } from '../../core/models/api-response.model';

type ModalType = 'create' | 'edit' | 'delete' | 'activate' | null;
type CampaignType = SmsCampaign['campaignType'];
type TriggerType  = SmsCampaign['triggerType'];

@Component({
  selector: 'app-sms-campaigns',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './sms-campaigns.html',
  styleUrl: './sms-campaigns.scss',
})
export class SmsCampaignsComponent implements OnInit {
  private readonly svc = inject(AdminService);

  campaigns: SmsCampaign[] = [];
  loading = true;
  error = '';

  // Pagination
  page = 0;
  total = 0;
  readonly pageSize = 20;

  // Selected campaign for messages panel
  selected: SmsCampaign | null = null;
  messages: SmsMessage[] = [];
  messagesLoading = false;

  // Modal state
  activeModal: ModalType = null;
  modalWorking = false;
  modalError = '';
  editingId: string | null = null;

  // Form fields
  formName = '';
  formType: CampaignType = 'ALL';
  formTrigger: TriggerType = 'DIRECT';
  formMessage = '';
  formRecurrence = '';
  formRunDate = '';

  readonly campaignTypes: CampaignType[] = ['INDIVIDUAL', 'ALL', 'QUERY'];
  readonly triggerTypes:  TriggerType[]  = ['DIRECT', 'SCHEDULED', 'TRIGGERED'];

  readonly rrulePresets: { label: string; value: string }[] = [
    { label: 'Daily',        value: 'FREQ=DAILY' },
    { label: 'Weekly (Mon)', value: 'FREQ=WEEKLY;BYDAY=MO' },
    { label: 'Monthly (1st)', value: 'FREQ=MONTHLY;BYMONTHDAY=1' },
    { label: 'Custom',       value: '' },
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.svc.listSmsCampaigns(this.page).subscribe({
      next:  (p: PageResponse<SmsCampaign>) => {
        this.campaigns = p.content;
        this.total     = p.totalElements;
        this.loading   = false;
      },
      error: () => { this.error = 'Failed to load campaigns.'; this.loading = false; },
    });
  }

  selectCampaign(c: SmsCampaign): void {
    if (this.selected?.id === c.id) {
      this.selected = null;
      return;
    }
    this.selected = c;
    this.messages = [];
    this.messagesLoading = true;
    this.svc.listSmsMessages(c.id).subscribe({
      next:  msgs => { this.messages = msgs; this.messagesLoading = false; },
      error: ()   => { this.messagesLoading = false; },
    });
  }

  // ── Modal helpers ──────────────────────────────────────────────────────────

  openCreate(): void {
    this.editingId     = null;
    this.formName      = '';
    this.formType      = 'ALL';
    this.formTrigger   = 'DIRECT';
    this.formMessage   = '';
    this.formRecurrence = '';
    this.formRunDate   = '';
    this.modalError    = '';
    this.activeModal   = 'create';
  }

  openEdit(c: SmsCampaign): void {
    this.editingId      = c.id;
    this.formName       = c.campaignName;
    this.formType       = c.campaignType;
    this.formTrigger    = c.triggerType;
    this.formMessage    = c.message;
    this.formRecurrence = c.recurrence ?? '';
    this.formRunDate    = c.runDate    ?? '';
    this.modalError     = '';
    this.activeModal    = 'edit';
  }

  openDelete(c: SmsCampaign): void {
    this.editingId  = c.id;
    this.modalError = '';
    this.activeModal = 'delete';
  }

  openActivate(c: SmsCampaign): void {
    this.editingId  = c.id;
    this.modalError = '';
    this.activeModal = 'activate';
  }

  closeModal(): void {
    if (!this.modalWorking) { this.activeModal = null; this.editingId = null; }
  }

  // ── Submit ─────────────────────────────────────────────────────────────────

  submitSave(): void {
    if (!this.formName.trim() || !this.formMessage.trim()) return;
    this.modalWorking = true;
    this.modalError   = '';
    const req: CreateSmsCampaignRequest = {
      campaignName: this.formName.trim(),
      campaignType: this.formType,
      triggerType:  this.formTrigger,
      message:      this.formMessage.trim(),
      recurrence:   this.formRecurrence || undefined,
      runDate:      this.formRunDate    || undefined,
    };

    const call$ = this.editingId
      ? this.svc.updateSmsCampaign(this.editingId, req)
      : this.svc.createSmsCampaign(req);

    call$.subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    if (!this.editingId) return;
    this.modalWorking = true;
    this.svc.deleteSmsCampaign(this.editingId).subscribe({
      next: () => {
        this.activeModal = null; this.modalWorking = false;
        if (this.selected?.id === this.editingId) this.selected = null;
        this.load();
      },
      error: () => { this.modalError = 'Delete failed.'; this.modalWorking = false; },
    });
  }

  submitActivate(): void {
    if (!this.editingId) return;
    this.modalWorking = true;
    this.svc.activateSmsCampaign(this.editingId).subscribe({
      next: updated => {
        this.activeModal = null; this.modalWorking = false;
        const idx = this.campaigns.findIndex(c => c.id === updated.id);
        if (idx !== -1) this.campaigns[idx] = updated;
        if (this.selected?.id === updated.id) this.selected = updated;
      },
      error: () => { this.modalError = 'Activation failed.'; this.modalWorking = false; },
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  get totalPages(): number { return Math.max(1, Math.ceil(this.total / this.pageSize)); }
  get startRow():   number { return this.total === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.total); }

  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if ((this.page + 1) * this.pageSize < this.total) { this.page++; this.load(); } }

  canActivate(c: SmsCampaign): boolean {
    return c.status === 'PENDING' || c.status === 'WAITING_FOR_ACTIVATION';
  }

  statusVariant(s: SmsCampaign['status']): 'success' | 'warning' | 'info' | 'neutral' | 'error' {
    if (s === 'ACTIVE')                    return 'success';
    if (s === 'WAITING_FOR_ACTIVATION')    return 'info';
    if (s === 'PENDING')                   return 'warning';
    return 'neutral';
  }

  deliveryVariant(s: SmsMessage['deliveryStatus']): 'success' | 'warning' | 'error' | 'neutral' {
    if (s === 'SENT')    return 'success';
    if (s === 'FAILED')  return 'error';
    if (s === 'INVALID') return 'error';
    return 'neutral';
  }

  deletingName(): string {
    return this.campaigns.find(c => c.id === this.editingId)?.campaignName ?? '';
  }

  activatingName(): string {
    return this.campaigns.find(c => c.id === this.editingId)?.campaignName ?? '';
  }
}
