import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { NotificationBellService } from './notification-bell.service';

describe('NotificationBellService', () => {
  let service: NotificationBellService;
  let api: Record<'get' | 'post', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({ count: 0 })),
      post: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [NotificationBellService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(NotificationBellService);
  });

  it('getUnreadCount returns the count, or 0 on error', () => {
    api.get.mockReturnValue(of({ count: 5 }));
    let count: { count: number } | undefined;
    service.getUnreadCount().subscribe(c => (count = c));
    expect(api.get).toHaveBeenCalledWith('/notifications/inbox/unread-count');
    expect(count).toEqual({ count: 5 });

    api.get.mockReturnValue(throwError(() => new Error('x')));
    let fallback: { count: number } | undefined;
    service.getUnreadCount().subscribe(c => (fallback = c));
    expect(fallback).toEqual({ count: 0 });
  });

  it('getInbox uses api.get with page/size params (not getPage), [] on error', () => {
    api.get.mockReturnValue(of([{ id: 'n1' }]));
    let inbox: unknown;
    service.getInbox(1, 5).subscribe(i => (inbox = i));
    expect(api.get).toHaveBeenCalledWith('/notifications/inbox', { page: 1, size: 5 });
    expect(inbox).toEqual([{ id: 'n1' }]);

    api.get.mockReturnValue(throwError(() => new Error('x')));
    let fallback: unknown;
    service.getInbox().subscribe(i => (fallback = i));
    expect(fallback).toEqual([]);
  });

  it('markAllRead posts to read-all and swallows errors', () => {
    service.markAllRead().subscribe();
    expect(api.post).toHaveBeenCalledWith('/notifications/inbox/read-all', {});

    api.post.mockReturnValue(throwError(() => new Error('x')));
    expect(() => service.markAllRead().subscribe()).not.toThrow();
  });
});
