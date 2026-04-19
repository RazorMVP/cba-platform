import { Injectable, inject } from '@angular/core';
import { ApiService } from '../../core/api/api.service';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

export interface InAppNotification {
  id: string;
  type: string;
  severity: 'INFO' | 'WARNING' | 'ERROR';
  title: string;
  message: string;
  entityType: string | null;
  entityId: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationBellService {
  private readonly api = inject(ApiService);

  // Returns the unwrapped { count } record
  getUnreadCount(): Observable<{ count: number }> {
    return this.api.get<{ count: number }>('/notifications/inbox/unread-count').pipe(
      catchError(() => of({ count: 0 }))
    );
  }

  // Uses getPage so pagination meta is available
  getInbox(page = 0, size = 10): Observable<InAppNotification[]> {
    return this.api.get<InAppNotification[]>('/notifications/inbox', { page, size }).pipe(
      catchError(() => of([]))
    );
  }

  markAllRead(): Observable<void> {
    return this.api.post<void>('/notifications/inbox/read-all', {}).pipe(
      catchError(() => of(undefined as any))
    );
  }
}
