import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { TellerService, Teller, TellerRequest } from './teller.service';

@Component({
  selector: 'app-teller-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './teller-list.html',
  styleUrl: './teller-list.scss',
})
export class TellerListComponent implements OnInit {
  private readonly svc = inject(TellerService);

  tellers: Teller[]         = [];
  filtered: Teller[]        = [];
  loading                   = true;
  searchQuery               = '';
  statusFilter              = '';

  // New teller modal
  showCreateModal = false;
  createWorking   = false;
  createError     = '';
  form: TellerRequest = { name: '', branchCode: '', startDate: '' };

  readonly statuses = ['', 'INACTIVE', 'ACTIVE', 'CLOSED'];

  ngOnInit(): void {
    this.svc.list().subscribe({
      next:  t  => { this.tellers = t; this.applyFilter(); this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.toLowerCase();
    this.filtered = this.tellers.filter(t => {
      const matchSearch = !q ||
        t.name.toLowerCase().includes(q) ||
        t.branchCode.toLowerCase().includes(q);
      const matchStatus = !this.statusFilter || t.status === this.statusFilter;
      return matchSearch && matchStatus;
    });
  }

  openCreateModal(): void {
    this.showCreateModal = true;
    this.createError     = '';
    this.createWorking   = false;
    this.form = { name: '', branchCode: '', startDate: new Date().toISOString().slice(0, 10) };
  }

  submitCreate(): void {
    if (!this.form.name || !this.form.branchCode || !this.form.startDate) return;
    this.createWorking = true;
    this.createError   = '';
    this.svc.create(this.form).subscribe({
      next: t => {
        this.tellers = [...this.tellers, t];
        this.applyFilter();
        this.createWorking   = false;
        this.showCreateModal = false;
      },
      error: () => { this.createError = 'Failed to create teller. Check the details and try again.'; this.createWorking = false; },
    });
  }

  closeCreateModal(): void { if (!this.createWorking) this.showCreateModal = false; }

  statusVariant(s: string): 'success' | 'warning' | 'error' | 'neutral' {
    return s === 'ACTIVE' ? 'success' : s === 'INACTIVE' ? 'warning' : 'neutral';
  }
}
