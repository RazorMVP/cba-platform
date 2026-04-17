import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, Survey, CreateSurveyRequest } from './system.service';

@Component({
  selector: 'app-surveys',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './surveys.html',
  styleUrl: './surveys.scss',
})
export class SurveysComponent implements OnInit {
  private readonly svc = inject(SystemService);

  surveys: Survey[] = [];
  loading = true;
  error   = '';

  expandedSurvey: string | null = null;

  activeModal: 'create' | 'edit' | 'delete' | null = null;
  editTarget:   Survey | null = null;
  deleteTarget: Survey | null = null;
  working    = false;
  modalError = '';

  form: CreateSurveyRequest = this.blank();

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listSurveys().subscribe({
      next: list => { this.surveys = list; this.loading = false; },
      error: () => { this.error = 'Failed to load surveys.'; this.loading = false; },
    });
  }

  toggleExpand(id: string): void {
    this.expandedSurvey = this.expandedSurvey === id ? null : id;
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(s: Survey): void {
    this.form = { name: s.name, key: s.key, countryCode: s.countryCode, description: s.description ?? '' };
    this.editTarget = s;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  openDelete(s: Survey): void {
    this.deleteTarget = s;
    this.working = false;
    this.activeModal = 'delete';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateSurvey(this.editTarget.id, this.form)
      : this.svc.createSurvey(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.working = true;
    this.svc.deleteSurvey(this.deleteTarget.id).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  private blank(): CreateSurveyRequest {
    return { name: '', key: '', countryCode: '', description: '' };
  }
}
