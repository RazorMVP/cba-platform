import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NotificationBellComponent } from './notification-bell';
import { NotificationBellService, InAppNotification } from './notification-bell.service';

type Svc = Record<'getUnreadCount' | 'getInbox' | 'markAllRead', ReturnType<typeof vi.fn>>;

function notif(over: Partial<InAppNotification> = {}): InAppNotification {
  return {
    id: 'n1', type: 'LOAN', severity: 'INFO', title: 'Hi', message: 'msg',
    entityType: null, entityId: null, createdAt: new Date().toISOString(), ...over,
  };
}

describe('NotificationBellComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getUnreadCount: vi.fn().mockReturnValue(of({ count: 3 })),
      getInbox: vi.fn().mockReturnValue(of([notif()])),
      markAllRead: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [{ provide: NotificationBellService, useValue: svc }],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function make() {
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges(); // ngOnInit + full template render
    return fixture.componentInstance;
  }

  it('loads the unread count on init and renders without error', () => {
    const c = make();
    expect(svc.getUnreadCount).toHaveBeenCalled();
    expect(c.unreadCount).toBe(3);
    expect(c.open).toBe(false);
  });

  describe('togglePanel', () => {
    it('opens the panel and lazy-loads notifications the first time', () => {
      const c = make();
      c.togglePanel();
      expect(c.open).toBe(true);
      expect(svc.getInbox).toHaveBeenCalledWith(0, 15);
      expect(c.notifications).toHaveLength(1);
      expect(c.loading).toBe(false);
    });

    it('closes the panel without reloading when toggled again', () => {
      const c = make();
      c.togglePanel(); // open + load
      svc.getInbox.mockClear();
      c.togglePanel(); // close
      expect(c.open).toBe(false);
      expect(svc.getInbox).not.toHaveBeenCalled();
    });

    it('does not reload the inbox when notifications already present', () => {
      const c = make();
      c.notifications = [notif()];
      c.togglePanel();
      expect(svc.getInbox).not.toHaveBeenCalled();
    });
  });

  it('markAllRead clears count, list and closes the panel', () => {
    const c = make();
    c.open = true;
    c.notifications = [notif()];
    c.markAllRead();
    expect(svc.markAllRead).toHaveBeenCalled();
    expect(c.unreadCount).toBe(0);
    expect(c.notifications).toHaveLength(0);
    expect(c.open).toBe(false);
  });

  it('polls getUnreadCount every 30 seconds', () => {
    vi.useFakeTimers();
    svc.getUnreadCount.mockReturnValue(of({ count: 1 }));
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges(); // startWith(0) fires immediately
    expect(svc.getUnreadCount).toHaveBeenCalledTimes(1);

    svc.getUnreadCount.mockReturnValue(of({ count: 7 }));
    vi.advanceTimersByTime(30_000);
    expect(svc.getUnreadCount).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.unreadCount).toBe(7);
  });

  it('stops polling after destroy', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    expect(svc.getUnreadCount).toHaveBeenCalledTimes(1);
    fixture.destroy();
    vi.advanceTimersByTime(60_000);
    expect(svc.getUnreadCount).toHaveBeenCalledTimes(1);
  });

  describe('presentation helpers', () => {
    let c: NotificationBellComponent;
    beforeEach(() => { c = make(); });

    it('severityIcon maps severities', () => {
      expect(c.severityIcon('ERROR')).toBe('error');
      expect(c.severityIcon('WARNING')).toBe('warning');
      expect(c.severityIcon('INFO')).toBe('info');
      expect(c.severityIcon('anything')).toBe('info');
    });

    it('timeAgo buckets the elapsed time', () => {
      const now = Date.now();
      expect(c.timeAgo(new Date(now - 5_000).toISOString())).toBe('5s ago');
      expect(c.timeAgo(new Date(now - 120_000).toISOString())).toBe('2m ago');
      expect(c.timeAgo(new Date(now - 2 * 3_600_000).toISOString())).toBe('2h ago');
      expect(c.timeAgo(new Date(now - 2 * 86_400_000).toISOString())).toBe('2d ago');
    });
  });

  describe('onDocumentClick', () => {
    it('closes an open panel on outside click', () => {
      const c = make();
      c.open = true;
      const outside = document.createElement('div');
      c.onDocumentClick({ target: outside } as unknown as MouseEvent);
      expect(c.open).toBe(false);
    });

    it('keeps the panel open on inside click', () => {
      const c = make();
      c.open = true;
      const inside = document.createElement('div');
      inside.closest = () => document.createElement('app-notification-bell');
      c.onDocumentClick({ target: inside } as unknown as MouseEvent);
      expect(c.open).toBe(true);
    });
  });
});
