import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, SecurityPolicy } from './admin.service';

@Component({
  selector: 'app-security-policy',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './security-policy.html',
  styleUrls: ['./security-policy.scss']
})
export class SecurityPolicyComponent implements OnInit {
  private svc = inject(AdminService);

  policy  = signal<SecurityPolicy | null>(null);
  form    = signal<Partial<SecurityPolicy>>({});
  loading = signal(true);
  saving  = signal(false);
  saved   = signal(false);
  error   = signal<string | null>(null);
  editMode = signal(false);

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.loading.set(true);
    this.svc.getSecurityPolicy().subscribe({
      next: p => { this.policy.set(p); this.loading.set(false); },
      error: () => { this.error.set('Could not load security policy — Keycloak may be unavailable.'); this.loading.set(false); }
    });
  }

  enterEdit(): void {
    const p = this.policy();
    if (!p) return;
    this.form.set({ ...p });
    this.editMode.set(true);
    this.saved.set(false);
  }

  cancel(): void { this.editMode.set(false); }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.svc.updateSecurityPolicy(this.form()).subscribe({
      next: p => { this.policy.set(p); this.saving.set(false); this.editMode.set(false); this.saved.set(true); },
      error: e => { this.error.set(e?.error?.errors?.[0]?.message ?? 'Save failed'); this.saving.set(false); }
    });
  }

  setForm<K extends keyof SecurityPolicy>(key: K, value: SecurityPolicy[K]): void {
    this.form.update(f => ({ ...f, [key]: value }));
  }

  secondsToMinutes(s: number): number { return Math.round(s / 60); }
  minutesToSeconds(m: number): number { return m * 60; }
}
