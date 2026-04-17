import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, ExchangeRateResponse, ExchangeRateRequest } from './system.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-exchange-rates',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './exchange-rates.html',
  styleUrl: './exchange-rates.scss',
})
export class ExchangeRatesComponent implements OnInit {
  private readonly svc = inject(SystemService);

  rates: ExchangeRateResponse[] = [];
  loading = true;
  error   = '';

  activeModal: 'upsert' | 'deactivate' | null = null;
  deactivateTarget: ExchangeRateResponse | null = null;
  working    = false;
  modalError = '';

  form: ExchangeRateRequest = { fromCurrency: '', toCurrency: '', rate: 0 };

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listExchangeRates().subscribe({
      next: r => { this.rates = r; this.loading = false; },
      error: () => { this.error = 'Failed to load exchange rates.'; this.loading = false; },
    });
  }

  openUpsert(r?: ExchangeRateResponse): void {
    this.form = r
      ? { fromCurrency: r.fromCurrency, toCurrency: r.toCurrency, rate: r.rate }
      : { fromCurrency: '', toCurrency: '', rate: 0 };
    this.modalError = '';
    this.working = false;
    this.activeModal = 'upsert';
  }

  openDeactivate(r: ExchangeRateResponse): void {
    this.deactivateTarget = r;
    this.working = false;
    this.activeModal = 'deactivate';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    this.svc.upsertExchangeRate(this.form).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDeactivate(): void {
    if (!this.deactivateTarget) return;
    this.working = true;
    const { fromCurrency, toCurrency } = this.deactivateTarget;
    this.svc.deactivateExchangeRate(fromCurrency, toCurrency).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  activeRates(): ExchangeRateResponse[] { return this.rates.filter(r => r.active); }
  inactiveRates(): ExchangeRateResponse[] { return this.rates.filter(r => !r.active); }
}
