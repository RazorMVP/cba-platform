import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NotificationsComponent } from './notifications';
import { AdminService, NotificationTemplate } from './admin.service';
import { NotificationBellService } from '../../layout/notification-bell/notification-bell.service';

type Svc = Record<
  'listNotificationTemplates' | 'createNotificationTemplate' | 'updateNotificationTemplate' |
  'deactivateNotificationTemplate' | 'sendTestNotification' | 'listNotificationHistory',
  ReturnType<typeof vi.fn>
>;
type Bell = Record<'getInbox', ReturnType<typeof vi.fn>>;

function tpl(over: Partial<NotificationTemplate> = {}): NotificationTemplate {
  return { id: 't1', name: 'Welcome', eventType: 'ACCOUNT_OPENED', deliveryMethod: 'EMAIL', subject: 'Hi', body: 'Body', active: true, ...over };
}

describe('NotificationsComponent', () => {
  let svc: Svc;
  let bell: Bell;

  beforeEach(() => {
    svc = {
      listNotificationTemplates: vi.fn().mockReturnValue(of([tpl()])),
      createNotificationTemplate: vi.fn().mockReturnValue(of(tpl({ id: 't2' }))),
      updateNotificationTemplate: vi.fn().mockReturnValue(of(tpl())),
      deactivateNotificationTemplate: vi.fn().mockReturnValue(of(void 0)),
      sendTestNotification: vi.fn().mockReturnValue(of({ id: 'log1', status: 'SENT' })),
      listNotificationHistory: vi.fn().mockReturnValue(of([{ id: 'log1', eventType: 'ACCOUNT_OPENED' }])),
    };
    bell = { getInbox: vi.fn().mockReturnValue(of([{ id: 'n1', severity: 'INFO', title: 'x', message: 'y' }])) };
    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        provideRouter([]),
        { provide: AdminService, useValue: svc },
        { provide: NotificationBellService, useValue: bell },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(NotificationsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads templates on init', () => {
    const c = make();
    expect(svc.listNotificationTemplates).toHaveBeenCalled();
    expect(c.templates).toHaveLength(1);
    expect(c.tplLoading).toBe(false);
  });

  it('flags an error when templates fail to load', () => {
    svc.listNotificationTemplates.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.tplError).toBe('Failed to load templates.');
  });

  describe('switchTab', () => {
    it('lazy-loads history once', () => {
      const c = make();
      c.switchTab('history');
      expect(svc.listNotificationHistory).toHaveBeenCalledTimes(1);
      c.switchTab('templates');
      c.switchTab('history');
      expect(svc.listNotificationHistory).toHaveBeenCalledTimes(1);
    });
    it('lazy-loads the in-app feed once', () => {
      const c = make();
      c.switchTab('feed');
      expect(bell.getInbox).toHaveBeenCalledWith(0, 50);
      expect(c.feed).toHaveLength(1);
      expect(c.feedLoaded).toBe(true);
    });
  });

  it('loadFeed sets an error on failure', () => {
    bell.getInbox.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    c.loadFeed();
    expect(c.feedError).toBe('Failed to load feed.');
  });

  it('feedSeverityVariant maps severities', () => {
    const c = make();
    expect(c.feedSeverityVariant('ERROR')).toBe('error');
    expect(c.feedSeverityVariant('WARNING')).toBe('warning');
    expect(c.feedSeverityVariant('INFO')).toBe('info');
  });

  describe('saveTemplate', () => {
    it('creates when there is no editingId', () => {
      const c = make();
      c.openCreate();
      c.saveTemplate();
      expect(svc.createNotificationTemplate).toHaveBeenCalled();
      expect(c.showModal).toBe(false);
    });
    it('updates when editing', () => {
      const c = make();
      c.openEdit(tpl());
      c.saveTemplate();
      expect(svc.updateNotificationTemplate).toHaveBeenCalledWith('t1', expect.any(Object));
    });
    it('surfaces an error on failure', () => {
      svc.createNotificationTemplate.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.saveTemplate();
      expect(c.modalError).toBe('Save failed.');
    });
  });

  it('applyFilter resets histLoaded and reloads', () => {
    const c = make();
    c.histFilter = 'LOAN_APPROVED';
    c.applyFilter();
    expect(svc.listNotificationHistory).toHaveBeenCalledWith({ eventType: 'LOAN_APPROVED' });
  });

  describe('sendTest', () => {
    it('does nothing without a recipient', () => {
      const c = make();
      c.openTest(tpl());
      c.sendTest();
      expect(svc.sendTestNotification).not.toHaveBeenCalled();
    });
    it('sends and records the result', () => {
      const c = make();
      c.openTest(tpl());
      c.testRecipient = 'jane@x.com';
      c.sendTest();
      expect(svc.sendTestNotification).toHaveBeenCalledWith('t1', 'jane@x.com');
      expect(c.testResult).not.toBeNull();
    });
    it('surfaces an error on failure', () => {
      svc.sendTestNotification.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openTest(tpl());
      c.testRecipient = 'jane@x.com';
      c.sendTest();
      expect(c.testError).toBe('Test send failed.');
    });
  });

  it('statusVariant + methodIcon helpers', () => {
    const c = make();
    expect(c.statusVariant('SENT')).toBe('success');
    expect(c.statusVariant('FAILED')).toBe('error');
    expect(c.statusVariant('SKIPPED')).toBe('neutral');
    expect(c.methodIcon('EMAIL')).toBe('email');
    expect(c.methodIcon('SMS')).toBe('sms');
  });
});
