import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, GlobalConfig, UpdateConfigRequest } from './system.service';

@Component({
  selector: 'app-global-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './global-config.html',
  styleUrl: './global-config.scss',
})
export class GlobalConfigComponent implements OnInit {
  private readonly svc = inject(SystemService);

  configs:  GlobalConfig[] = [];
  loading   = true;
  error     = '';

  editingId   = '';
  editForm: UpdateConfigRequest = { enabled: false };
  editWorking = false;
  editError   = '';

  searchQuery = '';
  get filtered(): GlobalConfig[] {
    const q = this.searchQuery.toLowerCase();
    return q ? this.configs.filter(c => c.name.toLowerCase().includes(q)) : this.configs;
  }

  ngOnInit(): void {
    this.svc.listConfigurations().subscribe({
      next: list => { this.configs = list; this.loading = false; },
      error: () => { this.error = 'Failed to load configuration.'; this.loading = false; },
    });
  }

  startEdit(c: GlobalConfig): void {
    this.editingId = c.id;
    this.editForm  = {
      enabled:      c.enabled,
      stringValue:  c.stringValue  ?? '',
      numericValue: c.numericValue ?? undefined,
      booleanValue: c.booleanValue ?? undefined,
    };
    this.editWorking = false; this.editError = '';
  }

  cancelEdit(): void { this.editingId = ''; }

  saveEdit(c: GlobalConfig): void {
    this.editWorking = true;
    this.svc.updateConfiguration(c.id, this.editForm).subscribe({
      next: updated => {
        this.configs = this.configs.map(x => x.id === updated.id ? updated : x);
        this.editingId = ''; this.editWorking = false;
      },
      error: () => { this.editError = 'Failed to save.'; this.editWorking = false; },
    });
  }

  valueDisplay(c: GlobalConfig): string {
    if (c.stringValue  != null) return c.stringValue  || '—';
    if (c.numericValue != null) return String(c.numericValue);
    if (c.booleanValue != null) return c.booleanValue ? 'true' : 'false';
    return '—';
  }

  valueType(c: GlobalConfig): 'string' | 'number' | 'boolean' | 'none' {
    if (c.stringValue  != null) return 'string';
    if (c.numericValue != null) return 'number';
    if (c.booleanValue != null) return 'boolean';
    return 'none';
  }
}
