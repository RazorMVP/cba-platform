import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SystemService, Code, CodeValue, CreateCodeRequest, CreateCodeValueRequest,
} from './system.service';

interface CodeValueForm {
  name: string;
  description: string;
  position: number;
}

@Component({
  selector: 'app-codes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './codes.html',
  styleUrl: './codes.scss',
})
export class CodesComponent implements OnInit {
  private readonly svc = inject(SystemService);

  codes:   Code[] = [];
  loading  = true;
  error    = '';

  expandedCodeId = '';
  searchQuery    = '';

  get filtered(): Code[] {
    const q = this.searchQuery.toLowerCase();
    return q ? this.codes.filter(c => c.name.toLowerCase().includes(q)) : this.codes;
  }

  // ── Create code modal ──────────────────────────────────────────────────────
  createCodeModal = false;
  newCodeName     = '';
  codeWorking     = false;
  codeError       = '';

  // ── Inline value form per code ─────────────────────────────────────────────
  addingValueForCodeId    = '';
  editingValueId          = '';
  valueForm: CodeValueForm = this.blankValueForm();
  valueWorking            = false;
  valueError              = '';

  ngOnInit(): void {
    this.svc.listCodes().subscribe({
      next: list => { this.codes = list; this.loading = false; },
      error: () => { this.error = 'Failed to load codes.'; this.loading = false; },
    });
  }

  toggleExpand(codeId: string): void {
    if (this.expandedCodeId === codeId) {
      this.expandedCodeId = '';
    } else {
      this.expandedCodeId = codeId;
      // load values if not already populated
      const code = this.codes.find(c => c.id === codeId);
      if (code && !code.codeValues?.length) {
        this.svc.listCodeValues(codeId).subscribe({
          next: vals => {
            this.codes = this.codes.map(c => c.id === codeId ? { ...c, codeValues: vals } : c);
          },
        });
      }
    }
    this.addingValueForCodeId = '';
    this.editingValueId = '';
  }

  openAddValue(codeId: string): void {
    this.addingValueForCodeId = codeId; this.editingValueId = '';
    this.valueForm = this.blankValueForm(); this.valueWorking = false; this.valueError = '';
  }

  openEditValue(codeId: string, v: CodeValue): void {
    this.editingValueId = v.id; this.addingValueForCodeId = codeId;
    this.valueForm = { name: v.name, description: v.description, position: v.position };
    this.valueWorking = false; this.valueError = '';
  }

  cancelValue(): void { this.addingValueForCodeId = ''; this.editingValueId = ''; }

  submitValue(): void {
    if (!this.valueForm.name) return;
    this.valueWorking = true;
    const req: CreateCodeValueRequest = { ...this.valueForm };
    const codeId = this.addingValueForCodeId;
    const call = this.editingValueId
      ? this.svc.updateCodeValue(codeId, this.editingValueId, req)
      : this.svc.createCodeValue(codeId, req);
    call.subscribe({
      next: val => {
        this.codes = this.codes.map(c => {
          if (c.id !== codeId) return c;
          const existing = c.codeValues ?? [];
          const updated = this.editingValueId
            ? existing.map(v => v.id === val.id ? val : v)
            : [...existing, val];
          return { ...c, codeValues: updated };
        });
        this.addingValueForCodeId = ''; this.editingValueId = '';
        this.valueWorking = false;
      },
      error: () => { this.valueError = 'Failed to save value.'; this.valueWorking = false; },
    });
  }

  deleteValue(codeId: string, valueId: string): void {
    this.svc.deleteCodeValue(codeId, valueId).subscribe({
      next: () => {
        this.codes = this.codes.map(c => c.id === codeId
          ? { ...c, codeValues: (c.codeValues ?? []).filter(v => v.id !== valueId) }
          : c);
      },
    });
  }

  submitCreateCode(): void {
    if (!this.newCodeName) return;
    this.codeWorking = true;
    const req: CreateCodeRequest = { name: this.newCodeName };
    this.svc.createCode(req).subscribe({
      next: c => {
        this.codes = [...this.codes, { ...c, codeValues: [] }];
        this.createCodeModal = false; this.newCodeName = ''; this.codeWorking = false;
      },
      error: () => { this.codeError = 'Failed to create code.'; this.codeWorking = false; },
    });
  }

  deleteCode(c: Code): void {
    if (c.systemDefined) return;
    this.svc.deleteCode(c.id).subscribe({
      next: () => { this.codes = this.codes.filter(x => x.id !== c.id); },
    });
  }

  private blankValueForm(): CodeValueForm { return { name: '', description: '', position: 0 }; }
}
