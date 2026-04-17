import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, DataTable, CreateDataTableRequest } from './system.service';

@Component({
  selector: 'app-datatables',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './datatables.html',
  styleUrl: './datatables.scss',
})
export class DataTablesComponent implements OnInit {
  private readonly svc = inject(SystemService);

  tables:  DataTable[] = [];
  loading = true;
  error   = '';

  expandedTable: string | null = null;

  activeModal: 'create' | 'delete' | null = null;
  deleteTarget: DataTable | null = null;
  working    = false;
  modalError = '';

  form: CreateDataTableRequest = this.blank();

  readonly columnTypes = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'TEXT'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listDataTables().subscribe({
      next: list => { this.tables = list; this.loading = false; },
      error: () => { this.error = 'Failed to load data tables.'; this.loading = false; },
    });
  }

  toggleExpand(name: string): void {
    this.expandedTable = this.expandedTable === name ? null : name;
  }

  openCreate(): void {
    this.form = this.blank();
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openDelete(t: DataTable): void {
    this.deleteTarget = t;
    this.working = false;
    this.activeModal = 'delete';
  }

  closeModal(): void { this.activeModal = null; }

  addColumn(): void {
    this.form.columns.push({ columnName: '', columnType: 'STRING', columnLength: null, nullable: true, unique: false });
  }

  removeColumn(index: number): void {
    this.form.columns.splice(index, 1);
  }

  save(): void {
    this.working = true;
    this.modalError = '';
    this.svc.createDataTable(this.form).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.working = true;
    this.svc.deleteDataTable(this.deleteTarget.registeredTableName).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  get canSave(): boolean {
    return !!this.form.registeredTableName.trim() &&
           !!this.form.applicationTableName.trim() &&
           this.form.columns.length > 0 &&
           this.form.columns.every(c => !!c.columnName.trim());
  }

  private blank(): CreateDataTableRequest {
    return {
      registeredTableName: '', applicationTableName: '', allowMultipleRows: false,
      columns: [{ columnName: '', columnType: 'STRING', columnLength: null, nullable: true, unique: false }],
    };
  }
}
