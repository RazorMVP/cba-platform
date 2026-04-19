import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, BulkImportResult, BulkImportJob } from './admin.service';

type EntityType = 'CUSTOMERS' | 'LOANS';

@Component({
  selector: 'app-bulk-import',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bulk-import.html',
  styleUrls: ['./bulk-import.scss']
})
export class BulkImportComponent {
  private svc = inject(AdminService);

  entityType: EntityType = 'CUSTOMERS';
  selectedFile: File | null = null;
  uploading = signal(false);
  result = signal<BulkImportResult | null>(null);
  error = signal<string | null>(null);
  jobs = signal<BulkImportJob[]>([]);
  showHistory = signal(false);

  readonly templates: Record<EntityType, string> = {
    CUSTOMERS: 'firstName,lastName,email,phone,nationalId,dateOfBirth,notes\nJohn,Doe,john.doe@example.com,+254712345678,ID123456,1990-05-15,',
    LOANS:     'customerId,productId,linkedAccountId,principalAmount,termMonths,notes\n<uuid>,<uuid>,<uuid>,50000.00,24,'
  };

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.result.set(null);
    this.error.set(null);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.selectedFile = event.dataTransfer?.files[0] ?? null;
    this.result.set(null);
    this.error.set(null);
  }

  onDragOver(event: DragEvent): void { event.preventDefault(); }

  upload(): void {
    if (!this.selectedFile) return;
    this.uploading.set(true);
    this.result.set(null);
    this.error.set(null);

    const obs = this.entityType === 'CUSTOMERS'
      ? this.svc.importCustomers(this.selectedFile)
      : this.svc.importLoans(this.selectedFile);

    obs.subscribe({
      next: r => { this.result.set(r); this.uploading.set(false); this.loadHistory(); },
      error: e => { this.error.set(e?.error?.errors?.[0]?.message ?? 'Upload failed'); this.uploading.set(false); }
    });
  }

  downloadTemplate(): void {
    const content = this.templates[this.entityType];
    const blob = new Blob([content], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.entityType.toLowerCase()}-template.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  toggleHistory(): void {
    this.showHistory.update(v => !v);
    if (this.showHistory() && this.jobs().length === 0) this.loadHistory();
  }

  private loadHistory(): void {
    this.svc.bulkImportJobs(this.entityType).subscribe({ next: j => this.jobs.set(j) });
  }

  statusClass(s: string): string {
    return s === 'COMPLETED' ? 'success' : s === 'PARTIAL' ? 'warning' : 'error';
  }
}
