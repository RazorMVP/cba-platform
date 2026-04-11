import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import {
  GroupsService, Group, GroupMember, CollectionSheet, GlimAccount,
} from '../groups.service';

type Tab = 'members' | 'collection-sheet' | 'glim';

@Component({
  selector: 'app-group-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './group-detail.html',
  styleUrl: './group-detail.scss',
})
export class GroupDetailComponent implements OnInit {
  private readonly svc   = inject(GroupsService);
  private readonly route = inject(ActivatedRoute);

  group:   Group | null           = null;
  members: GroupMember[]          = [];
  sheet:   CollectionSheet | null = null;
  glim:    GlimAccount[]          = [];

  loading      = true;
  error        = '';
  activeTab:   Tab = 'members';
  sheetDate    = '';
  sheetLoading = false;

  // ── Add member modal ───────────────────────────────────────────────────────
  addMemberModal = false;
  newCustomerId  = '';
  memberWorking  = false;
  memberError    = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getGroup(id).subscribe({
      next: g => { this.group = g; this.loading = false; this.loadMembers(id); },
      error: () => { this.error = 'Failed to load group.'; this.loading = false; },
    });
  }

  loadMembers(id: string): void {
    this.svc.getGroupMembers(id).subscribe({ next: m => this.members = m });
  }

  switchTab(tab: Tab): void {
    this.activeTab = tab;
    if (!this.group) return;
    if (tab === 'glim' && this.glim.length === 0) {
      this.svc.getGlimAccounts(this.group.id).subscribe({ next: g => this.glim = g });
    }
  }

  generateSheet(): void {
    if (!this.group || !this.sheetDate) return;
    this.sheetLoading = true;
    this.svc.generateCollectionSheet(this.group.id, this.sheetDate).subscribe({
      next: s => { this.sheet = s; this.sheetLoading = false; },
      error: () => { this.sheetLoading = false; },
    });
  }

  activateGroup(): void {
    if (!this.group) return;
    this.svc.activateGroup(this.group.id).subscribe({
      next: g => { this.group = g; },
    });
  }

  openAddMember(): void {
    this.addMemberModal = true; this.newCustomerId = '';
    this.memberWorking = false; this.memberError = '';
  }

  submitAddMember(): void {
    if (!this.group || !this.newCustomerId) return;
    this.memberWorking = true;
    this.svc.addMember(this.group.id, this.newCustomerId).subscribe({
      next: () => {
        this.loadMembers(this.group!.id);
        this.addMemberModal = false; this.memberWorking = false;
      },
      error: () => { this.memberError = 'Failed to add member.'; this.memberWorking = false; },
    });
  }

  removeMember(customerId: string): void {
    if (!this.group) return;
    this.svc.removeMember(this.group.id, customerId).subscribe({
      next: () => { this.members = this.members.filter(m => m.customerId !== customerId); },
    });
  }

  get totalDue(): number      { return this.sheet?.items.reduce((s, i) => s + i.dueAmount, 0) ?? 0; }
  get totalCollected(): number { return this.sheet?.items.reduce((s, i) => s + i.paidAmount, 0) ?? 0; }

  statusVariant(s: string): 'success' | 'warning' | 'neutral' {
    if (s === 'ACTIVE') return 'success';
    if (s === 'PENDING') return 'warning';
    return 'neutral';
  }
}
