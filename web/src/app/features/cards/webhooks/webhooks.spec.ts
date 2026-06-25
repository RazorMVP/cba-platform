import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { WebhooksComponent } from './webhooks';
import { CardsService, Webhook, WebhookDelivery } from '../cards.service';

type Svc = Record<
  'listWebhooks' | 'registerWebhook' | 'deleteWebhook' | 'listDeliveries',
  ReturnType<typeof vi.fn>
>;

function webhook(over: Partial<Webhook> = {}): Webhook {
  return {
    id: 'w1', name: 'Hook', callbackUrl: 'https://x/y', events: ['CARD.ISSUED'],
    active: true, createdAt: '2026-01-01', ...over,
  };
}
function delivery(over: Partial<WebhookDelivery> = {}): WebhookDelivery {
  return {
    id: 'wd1', webhookId: 'w1', eventType: 'CARD.ISSUED', deliveryUuid: 'uuid',
    httpStatus: 200, status: 'DELIVERED', attemptCount: 1, lastAttemptAt: null, nextRetryAt: null, ...over,
  };
}

describe('WebhooksComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listWebhooks: vi.fn().mockReturnValue(of([webhook()])),
      registerWebhook: vi.fn().mockReturnValue(of(webhook({ id: 'w2' }))),
      deleteWebhook: vi.fn().mockReturnValue(of(void 0)),
      listDeliveries: vi.fn().mockReturnValue(of([delivery()])),
    };
    TestBed.configureTestingModule({
      imports: [WebhooksComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(WebhooksComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads webhooks on init', () => {
    const c = make();
    expect(svc.listWebhooks).toHaveBeenCalled();
    expect(c.webhooks).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false on error', () => {
    svc.listWebhooks.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('openCreate resets the form and opens the modal', () => {
    const c = make();
    c.form.name = 'dirty';
    c.openCreate();
    expect(c.showModal).toBe(true);
    expect(c.form).toEqual({ name: '', callbackUrl: '', events: [], secret: '' });
  });

  describe('event toggling', () => {
    it('toggleEvent adds then removes an event', () => {
      const c = make();
      c.toggleEvent('CARD.BLOCKED');
      expect(c.form.events).toEqual(['CARD.BLOCKED']);
      expect(c.hasEvent('CARD.BLOCKED')).toBe(true);
      c.toggleEvent('CARD.BLOCKED');
      expect(c.form.events).toEqual([]);
      expect(c.hasEvent('CARD.BLOCKED')).toBe(false);
    });
  });

  it('submit registers, closes and reloads', () => {
    const c = make();
    c.openCreate();
    c.form = { name: 'Hook', callbackUrl: 'https://x', events: ['CARD.ISSUED'], secret: 's' };
    svc.listWebhooks.mockClear();
    c.submit();
    expect(svc.registerWebhook).toHaveBeenCalledWith(c.form);
    expect(c.showModal).toBe(false);
    expect(svc.listWebhooks).toHaveBeenCalled();
  });

  it('deregister deletes then reloads', () => {
    const c = make();
    svc.listWebhooks.mockClear();
    c.deregister('w1');
    expect(svc.deleteWebhook).toHaveBeenCalledWith('w1');
    expect(svc.listWebhooks).toHaveBeenCalled();
  });

  describe('viewDeliveries', () => {
    it('selects the webhook and loads its deliveries', () => {
      const c = make();
      c.viewDeliveries(webhook({ id: 'w1' }));
      expect(c.selectedWebhook?.id).toBe('w1');
      expect(svc.listDeliveries).toHaveBeenCalledWith('w1');
      expect(c.deliveries).toHaveLength(1);
      expect(c.loadingDeliveries).toBe(false);
    });

    it('clears the loading flag on error', () => {
      svc.listDeliveries.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.viewDeliveries(webhook());
      expect(c.loadingDeliveries).toBe(false);
    });
  });

  it('deliveryVariant maps delivery statuses', () => {
    const c = make();
    expect(c.deliveryVariant('DELIVERED')).toBe('success');
    expect(c.deliveryVariant('FAILED')).toBe('error');
    expect(c.deliveryVariant('PENDING')).toBe('info');
  });

  it('groupByCategory buckets events by their prefix', () => {
    const c = make();
    const grouped = c.groupByCategory(['CARD.ISSUED', 'CARD.BLOCKED', 'FRAUD.RULE_TRIGGERED']);
    expect(grouped['CARD']).toEqual(['CARD.ISSUED', 'CARD.BLOCKED']);
    expect(grouped['FRAUD']).toEqual(['FRAUD.RULE_TRIGGERED']);
  });
});
