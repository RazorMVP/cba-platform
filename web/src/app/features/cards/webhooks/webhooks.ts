import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, Webhook, RegisterWebhookRequest, WebhookDelivery } from '../cards.service';

const ALL_EVENTS = [
  'AUTHORIZATION.APPROVED', 'AUTHORIZATION.DECLINED', 'AUTHORIZATION.REVERSED',
  'CARD.ISSUED', 'CARD.ACTIVATED', 'CARD.BLOCKED', 'CARD.UNBLOCKED', 'CARD.EXPIRED',
  'CARD.PIN_CHANGED', 'CARD.LIMIT_CHANGED',
  'FRAUD.RULE_TRIGGERED', 'FRAUD.CARD_STEP_UP', 'FRAUD.CARD_DECLINED_HIGH_RISK',
  'DISPUTE.RAISED', 'DISPUTE.RESOLVED',
];

@Component({
  selector: 'app-webhooks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './webhooks.html',
  styleUrl: './webhooks.scss',
})
export class WebhooksComponent implements OnInit {
  private readonly svc = inject(CardsService);

  webhooks: Webhook[] = [];
  loading = true;
  showModal = false;
  selectedWebhook: Webhook | null = null;
  deliveries: WebhookDelivery[] = [];
  loadingDeliveries = false;

  form: RegisterWebhookRequest = { name: '', callbackUrl: '', events: [], secret: '' };
  readonly allEvents = ALL_EVENTS;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listWebhooks().subscribe({ next: w => { this.webhooks = w; this.loading = false; }, error: () => { this.loading = false; } });
  }

  openCreate(): void { this.form = { name: '', callbackUrl: '', events: [], secret: '' }; this.showModal = true; }
  closeModal(): void { this.showModal = false; }

  toggleEvent(e: string): void {
    const idx = this.form.events.indexOf(e);
    if (idx >= 0) this.form.events.splice(idx, 1); else this.form.events.push(e);
  }

  hasEvent(e: string): boolean { return this.form.events.includes(e); }

  submit(): void {
    this.svc.registerWebhook(this.form).subscribe({ next: () => { this.closeModal(); this.load(); } });
  }

  deregister(id: string): void { this.svc.deleteWebhook(id).subscribe({ next: () => this.load() }); }

  viewDeliveries(w: Webhook): void {
    this.selectedWebhook = w;
    this.loadingDeliveries = true;
    this.svc.listDeliveries(w.id).subscribe({ next: d => { this.deliveries = d; this.loadingDeliveries = false; }, error: () => { this.loadingDeliveries = false; } });
  }

  deliveryVariant(s: WebhookDelivery['status']): 'success' | 'error' | 'info' { return ({ DELIVERED: 'success' as const, FAILED: 'error' as const, PENDING: 'info' as const })[s]; }

  groupByCategory(events: string[]): Record<string, string[]> {
    const out: Record<string, string[]> = {};
    events.forEach(e => {
      const cat = e.split('.')[0];
      (out[cat] = out[cat] || []).push(e);
    });
    return out;
  }
}
