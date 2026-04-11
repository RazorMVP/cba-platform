import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SystemService, TaxComponent, TaxGroup,
  CreateTaxComponentRequest, CreateTaxGroupRequest,
} from './system.service';

interface ComponentForm {
  name: string;
  percentage: number;
  startDate: string;
  creditAccountId: string;
  debitAccountId: string;
}

interface GroupComponentRow {
  taxComponentId: string;
  taxComponentName: string;
  startDate: string;
}

@Component({
  selector: 'app-taxes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './taxes.html',
  styleUrl: './taxes.scss',
})
export class TaxesComponent implements OnInit {
  private readonly svc = inject(SystemService);

  activeTab: 'components' | 'groups' = 'components';

  components: TaxComponent[] = [];
  groups:     TaxGroup[]     = [];
  loading     = true;
  error       = '';

  // ── Modal ────────────────────────────────────────────────────────────────────
  activeModal: 'create-component' | 'edit-component' | 'create-group' | 'edit-group' | 'delete-component' | 'delete-group' | null = null;
  editComponentTarget: TaxComponent | null = null;
  editGroupTarget:     TaxGroup | null     = null;
  modalWorking = false;
  modalError   = '';

  componentForm: ComponentForm = this.blankComponentForm();

  // Group form
  groupName = '';
  groupComponentRows: GroupComponentRow[] = [];

  ngOnInit(): void {
    this.svc.listTaxComponents().subscribe({
      next: list => { this.components = list; this.loading = false; },
      error: () => { this.error = 'Failed to load tax components.'; this.loading = false; },
    });
    this.svc.listTaxGroups().subscribe({
      next: list => { this.groups = list; },
    });
  }

  // ── Component CRUD ────────────────────────────────────────────────────────────
  openCreateComponent(): void {
    this.editComponentTarget = null;
    this.componentForm = this.blankComponentForm();
    this.activeModal = 'create-component';
    this.modalWorking = false; this.modalError = '';
  }

  openEditComponent(c: TaxComponent): void {
    this.editComponentTarget = c;
    this.componentForm = {
      name: c.name,
      percentage: c.percentage,
      startDate: c.startDate,
      creditAccountId: c.creditAccountId ?? '',
      debitAccountId:  c.debitAccountId  ?? '',
    };
    this.activeModal = 'edit-component';
    this.modalWorking = false; this.modalError = '';
  }

  openDeleteComponent(c: TaxComponent): void {
    this.editComponentTarget = c;
    this.activeModal = 'delete-component';
    this.modalWorking = false; this.modalError = '';
  }

  submitComponent(): void {
    if (!this.componentForm.name || !this.componentForm.startDate) return;
    this.modalWorking = true;
    const req: CreateTaxComponentRequest = {
      name: this.componentForm.name,
      percentage: this.componentForm.percentage,
      startDate: this.componentForm.startDate,
      creditAccountId: this.componentForm.creditAccountId || undefined,
      debitAccountId:  this.componentForm.debitAccountId  || undefined,
    };
    const call = this.editComponentTarget
      ? this.svc.updateTaxComponent(this.editComponentTarget.id, req)
      : this.svc.createTaxComponent(req);
    call.subscribe({
      next: saved => {
        if (this.editComponentTarget) {
          this.components = this.components.map(c => c.id === saved.id ? saved : c);
        } else {
          this.components = [...this.components, saved];
        }
        this.activeModal = null; this.modalWorking = false;
      },
      error: () => { this.modalError = 'Failed to save tax component.'; this.modalWorking = false; },
    });
  }

  // ── Group CRUD ────────────────────────────────────────────────────────────────
  openCreateGroup(): void {
    this.editGroupTarget = null;
    this.groupName = '';
    this.groupComponentRows = [this.blankGroupRow()];
    this.activeModal = 'create-group';
    this.modalWorking = false; this.modalError = '';
  }

  openEditGroup(g: TaxGroup): void {
    this.editGroupTarget = g;
    this.groupName = g.name;
    this.groupComponentRows = g.components.map(m => ({
      taxComponentId:   m.taxComponentId,
      taxComponentName: m.taxComponentName,
      startDate:        m.startDate,
    }));
    this.activeModal = 'edit-group';
    this.modalWorking = false; this.modalError = '';
  }

  openDeleteGroup(g: TaxGroup): void {
    this.editGroupTarget = g;
    this.activeModal = 'delete-group';
    this.modalWorking = false; this.modalError = '';
  }

  addGroupRow(): void { this.groupComponentRows.push(this.blankGroupRow()); }

  removeGroupRow(i: number): void {
    if (this.groupComponentRows.length > 1) this.groupComponentRows.splice(i, 1);
  }

  submitGroup(): void {
    if (!this.groupName || !this.groupComponentRows.length) return;
    this.modalWorking = true;
    const req: CreateTaxGroupRequest = {
      name: this.groupName,
      components: this.groupComponentRows
        .filter(r => r.taxComponentId)
        .map(r => ({ taxComponentId: r.taxComponentId, startDate: r.startDate })),
    };
    const call = this.editGroupTarget
      ? this.svc.updateTaxGroup(this.editGroupTarget.id, req)
      : this.svc.createTaxGroup(req);
    call.subscribe({
      next: saved => {
        if (this.editGroupTarget) {
          this.groups = this.groups.map(g => g.id === saved.id ? saved : g);
        } else {
          this.groups = [...this.groups, saved];
        }
        this.activeModal = null; this.modalWorking = false;
      },
      error: () => { this.modalError = 'Failed to save tax group.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { this.activeModal = null; }

  componentName(id: string): string {
    return this.components.find(c => c.id === id)?.name ?? id;
  }

  private blankComponentForm(): ComponentForm {
    return { name: '', percentage: 0, startDate: '', creditAccountId: '', debitAccountId: '' };
  }
  private blankGroupRow(): GroupComponentRow {
    return { taxComponentId: '', taxComponentName: '', startDate: '' };
  }
}
