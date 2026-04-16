import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { GroupsService, Center, Group, GroupMember } from '../groups.service';

type Tab = 'groups' | 'members';

@Component({
  selector: 'app-center-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent],
  templateUrl: './center-detail.html',
  styleUrl: './center-detail.scss',
})
export class CenterDetailComponent implements OnInit {
  private readonly svc   = inject(GroupsService);
  private readonly route = inject(ActivatedRoute);

  center:  Center | null = null;
  groups:  Group[]       = [];
  members: GroupMember[] = [];

  loading    = true;
  error      = '';
  activeTab: Tab = 'groups';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getCenter(id).subscribe({
      next: c => {
        this.center = c; this.loading = false;
        this.svc.getCenterGroups(id).subscribe({ next: g => this.groups = g });
      },
      error: () => { this.error = 'Failed to load center.'; this.loading = false; },
    });
  }

  switchTab(tab: Tab): void {
    this.activeTab = tab;
    if (!this.center) return;
    if (tab === 'members' && this.members.length === 0) {
      this.svc.getCenterMembers(this.center.id).subscribe({ next: m => this.members = m });
    }
  }

  activateCenter(): void {
    if (!this.center) return;
    this.svc.activateCenter(this.center.id).subscribe({ next: c => this.center = c });
  }

  statusVariant(s: string): 'success' | 'warning' | 'neutral' {
    if (s === 'ACTIVE') return 'success';
    if (s === 'PENDING') return 'warning';
    return 'neutral';
  }
}
