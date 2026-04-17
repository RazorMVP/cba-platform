import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountingService, GlClosure } from './accounting.service';
import { AdminService } from '../admin/admin.service';
import type { Office } from '../admin/admin.service';

@Component({
  selector: 'app-gl-closures',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './gl-closures.html',
  styleUrl: './gl-closures.scss',
})
export class GlClosuresComponent implements OnInit {
  private readonly svc      = inject(AccountingService);
  private readonly adminSvc = inject(AdminService);

  offices: Office[] = [];
  selectedOfficeId = '';

  closures: GlClosure[] = [];
  loading = false;
  error = '';

  // Create modal
  showCreateModal = false;
  form = { officeId: '', closingDate: '', comments: '' };
  saving = false;
  saveError = '';

  // Delete confirm modal
  showDeleteModal = false;
  deleteTarget: GlClosure | null = null;
  deleting = false;

  ngOnInit(): void {
    this.adminSvc.listOffices().subscribe({
      next: offices => {
        this.offices = offices;
        if (offices.length) {
          this.selectedOfficeId = offices[0].id;
          this.load();
        }
      },
    });
  }

  load(): void {
    if (!this.selectedOfficeId) return;
    this.loading = true;
    this.error = '';
    this.svc.listClosures(this.selectedOfficeId).subscribe({
      next:  c  => { this.closures = c; this.loading = false; },
      error: () => { this.error = 'Failed to load GL closures.'; this.loading = false; },
    });
  }

  onOfficeChange(): void {
    this.closures = [];
    this.load();
  }

  openCreate(): void {
    this.form = {
      officeId:    this.selectedOfficeId,
      closingDate: new Date().toISOString().slice(0, 10),
      comments:    '',
    };
    this.saveError = '';
    this.showCreateModal = true;
  }

  submitCreate(): void {
    if (!this.form.officeId || !this.form.closingDate) {
      this.saveError = 'Office and closing date are required.';
      return;
    }
    this.saving = true;
    this.saveError = '';
    this.svc.createClosure(this.form).subscribe({
      next: closure => {
        this.closures = [closure, ...this.closures];
        this.showCreateModal = false;
        this.saving = false;
      },
      error: () => { this.saveError = 'Failed to create closure. The date may already be closed for this office.'; this.saving = false; },
    });
  }

  selectedOfficeName(): string {
    return this.offices.find(o => o.id === this.selectedOfficeId)?.name ?? '';
  }
}
