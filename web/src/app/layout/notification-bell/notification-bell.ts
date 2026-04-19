import { Component, inject, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, interval } from 'rxjs';
import { takeUntil, switchMap, startWith } from 'rxjs/operators';
import { NotificationBellService, InAppNotification } from './notification-bell.service';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.scss',
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private readonly svc = inject(NotificationBellService);
  private readonly destroy$ = new Subject<void>();

  unreadCount = 0;
  open = false;
  notifications: InAppNotification[] = [];
  loading = false;

  ngOnInit(): void {
    // Poll unread count every 30 seconds
    interval(30_000).pipe(
      startWith(0),
      switchMap(() => this.svc.getUnreadCount()),
      takeUntil(this.destroy$)
    ).subscribe(r => this.unreadCount = r.count);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  togglePanel(): void {
    this.open = !this.open;
    if (this.open && this.notifications.length === 0) {
      this.loadNotifications();
    }
  }

  loadNotifications(): void {
    this.loading = true;
    this.svc.getInbox(0, 15).subscribe({
      next: items => { this.notifications = items; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  markAllRead(): void {
    this.svc.markAllRead().subscribe(() => {
      this.unreadCount = 0;
      this.notifications = [];
      this.open = false;
    });
  }

  severityIcon(severity: string): string {
    if (severity === 'ERROR')   return 'error';
    if (severity === 'WARNING') return 'warning';
    return 'info';
  }

  timeAgo(iso: string): string {
    const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
    if (diff < 60)   return `${diff}s ago`;
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  }

  // Close panel when clicking outside
  @HostListener('document:click', ['$event'])
  onDocumentClick(e: MouseEvent): void {
    const target = e.target as HTMLElement;
    if (this.open && !target.closest('app-notification-bell')) {
      this.open = false;
    }
  }
}
