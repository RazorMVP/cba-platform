import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SystemService,
  FieldConfiguration,
  UpdateFieldConfigRequest,
  CreateFieldConfigRequest,
} from './system.service';

@Component({
  selector: 'app-field-configuration',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './field-configuration.html',
  styleUrl: './field-configuration.scss',
})
export class FieldConfigurationComponent implements OnInit {
  private readonly svc = inject(SystemService);

  all: FieldConfiguration[] = [];
  loading = true;
  error = '';

  // Grouping
  entityTypes: string[] = [];
  activeEntity = '';

  get fields(): FieldConfiguration[] {
    return this.all.filter(f => f.entityType === this.activeEntity);
  }

  // Inline edit
  editingId = '';
  editForm: UpdateFieldConfigRequest = {};
  editWorking = false;
  editError = '';

  // Create modal
  showCreate = false;
  createForm: CreateFieldConfigRequest = { entityType: '', fieldName: '', fieldLabel: '' };
  createWorking = false;
  createError = '';

  // Delete confirm
  deletingId = '';
  deleteWorking = false;

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading = true;
    this.svc.listFieldConfigurations().subscribe({
      next: list => {
        this.all = list.sort((a, b) => a.displayOrder - b.displayOrder);
        this.entityTypes = [...new Set(list.map(f => f.entityType))].sort();
        if (!this.activeEntity && this.entityTypes.length) {
          this.activeEntity = this.entityTypes[0];
        }
        this.loading = false;
      },
      error: () => { this.error = 'Failed to load field configurations.'; this.loading = false; },
    });
  }

  selectEntity(et: string): void {
    this.activeEntity = et;
    this.editingId = '';
  }

  startEdit(f: FieldConfiguration): void {
    this.editingId = f.id;
    this.editForm = {
      fieldLabel:   f.fieldLabel,
      enabled:      f.enabled,
      mandatory:    f.mandatory,
      displayOrder: f.displayOrder,
      description:  f.description ?? '',
    };
    this.editWorking = false; this.editError = '';
  }

  cancelEdit(): void { this.editingId = ''; }

  saveEdit(f: FieldConfiguration): void {
    this.editWorking = true;
    this.svc.updateFieldConfig(f.id, this.editForm).subscribe({
      next: updated => {
        this.all = this.all.map(x => x.id === updated.id ? updated : x);
        this.editingId = ''; this.editWorking = false;
      },
      error: () => { this.editError = 'Save failed.'; this.editWorking = false; },
    });
  }

  openCreate(): void {
    this.createForm = { entityType: this.activeEntity, fieldName: '', fieldLabel: '',
                        enabled: true, mandatory: false, displayOrder: 0 };
    this.createError = ''; this.createWorking = false; this.showCreate = true;
  }

  submitCreate(): void {
    this.createWorking = true;
    this.svc.createFieldConfig(this.createForm).subscribe({
      next: created => {
        this.all = [...this.all, created].sort((a, b) => a.displayOrder - b.displayOrder);
        if (!this.entityTypes.includes(created.entityType)) {
          this.entityTypes = [...this.entityTypes, created.entityType].sort();
        }
        this.showCreate = false; this.createWorking = false;
      },
      error: () => { this.createError = 'Failed to create field.'; this.createWorking = false; },
    });
  }

  confirmDelete(id: string): void { this.deletingId = id; }
  cancelDelete(): void { this.deletingId = ''; }

  doDelete(): void {
    this.deleteWorking = true;
    this.svc.deleteFieldConfig(this.deletingId).subscribe({
      next: () => {
        this.all = this.all.filter(f => f.id !== this.deletingId);
        this.entityTypes = [...new Set(this.all.map(f => f.entityType))].sort();
        if (!this.entityTypes.includes(this.activeEntity) && this.entityTypes.length) {
          this.activeEntity = this.entityTypes[0];
        }
        this.deletingId = ''; this.deleteWorking = false;
      },
      error: () => { this.deleteWorking = false; },
    });
  }
}
