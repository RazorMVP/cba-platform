import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SystemService, FloatingRate, CreateFloatingRateRequest,
} from './system.service';

interface PeriodRow {
  fromDate: string;
  interestRate: number;
  isDifferentialToBaseLendingRate: boolean;
}

@Component({
  selector: 'app-floating-rates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './floating-rates.html',
  styleUrl: './floating-rates.scss',
})
export class FloatingRatesComponent implements OnInit {
  private readonly svc = inject(SystemService);

  rates:   FloatingRate[] = [];
  loading  = true;
  error    = '';

  expandedId = '';
  searchQuery = '';

  get filtered(): FloatingRate[] {
    const q = this.searchQuery.toLowerCase();
    return q ? this.rates.filter(r => r.name.toLowerCase().includes(q)) : this.rates;
  }

  // ── Modal ────────────────────────────────────────────────────────────────────
  activeModal: 'create' | 'edit' | 'delete' | null = null;
  editTarget: FloatingRate | null = null;
  modalWorking = false;
  modalError   = '';

  form = this.blankForm();
  periodRows: PeriodRow[] = [];

  ngOnInit(): void {
    this.svc.listFloatingRates().subscribe({
      next: list => { this.rates = list; this.loading = false; },
      error: () => { this.error = 'Failed to load floating rates.'; this.loading = false; },
    });
  }

  toggleExpand(id: string): void {
    this.expandedId = this.expandedId === id ? '' : id;
  }

  openCreate(): void {
    this.editTarget  = null;
    this.form        = this.blankForm();
    this.periodRows  = [this.blankPeriod()];
    this.activeModal = 'create';
    this.modalWorking = false; this.modalError = '';
  }

  openEdit(r: FloatingRate): void {
    this.editTarget = r;
    this.form = { name: r.name, isBaseLendingRate: r.isBaseLendingRate };
    this.periodRows = r.ratePeriods.map(p => ({
      fromDate: p.fromDate,
      interestRate: p.interestRate,
      isDifferentialToBaseLendingRate: p.isDifferentialToBaseLendingRate,
    }));
    this.activeModal = 'edit';
    this.modalWorking = false; this.modalError = '';
  }

  openDelete(r: FloatingRate): void {
    this.editTarget  = r;
    this.activeModal = 'delete';
    this.modalWorking = false; this.modalError = '';
  }

  closeModal(): void { this.activeModal = null; }

  addPeriod(): void { this.periodRows.push(this.blankPeriod()); }

  removePeriod(i: number): void {
    if (this.periodRows.length > 1) this.periodRows.splice(i, 1);
  }

  submitForm(): void {
    if (!this.form.name || !this.periodRows.length) return;
    this.modalWorking = true;
    const req: CreateFloatingRateRequest = {
      name: this.form.name,
      isBaseLendingRate: this.form.isBaseLendingRate,
      ratePeriods: this.periodRows.map(p => ({
        fromDate: p.fromDate,
        interestRate: p.interestRate,
        isDifferentialToBaseLendingRate: p.isDifferentialToBaseLendingRate,
      })),
    };
    const call = this.editTarget
      ? this.svc.updateFloatingRate(this.editTarget.id, req)
      : this.svc.createFloatingRate(req);
    call.subscribe({
      next: saved => {
        if (this.editTarget) {
          this.rates = this.rates.map(r => r.id === saved.id ? saved : r);
        } else {
          this.rates = [...this.rates, saved];
        }
        this.activeModal = null; this.modalWorking = false;
      },
      error: () => { this.modalError = 'Failed to save.'; this.modalWorking = false; },
    });
  }

  confirmDelete(): void {
    if (!this.editTarget) return;
    this.modalWorking = true;
    this.svc.deleteFloatingRate(this.editTarget.id).subscribe({
      next: () => {
        this.rates = this.rates.filter(r => r.id !== this.editTarget!.id);
        this.activeModal = null; this.modalWorking = false;
      },
      error: () => { this.modalError = 'Failed to delete.'; this.modalWorking = false; },
    });
  }

  private blankForm() { return { name: '', isBaseLendingRate: false }; }
  private blankPeriod(): PeriodRow { return { fromDate: '', interestRate: 0, isDifferentialToBaseLendingRate: false }; }
}
