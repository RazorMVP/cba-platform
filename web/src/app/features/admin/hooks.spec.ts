import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HooksComponent } from './hooks';
import { AdminService, Hook } from './admin.service';

type Svc = Record<'listHooks' | 'createHook' | 'updateHook' | 'deleteHook', ReturnType<typeof vi.fn>>;

function hook(over: Partial<Hook> = {}): Hook {
  return {
    id: 'h1', name: 'Webhook', hookType: 'WEB', url: 'https://x.com',
    events: ['LOAN_APPROVED'], enabled: true, createdAt: '2026-01-01', ...over,
  };
}

describe('HooksComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listHooks: vi.fn().mockReturnValue(of([hook()])),
      createHook: vi.fn().mockReturnValue(of(hook({ id: 'h2', name: 'New' }))),
      updateHook: vi.fn().mockReturnValue(of(hook({ name: 'Edited' }))),
      deleteHook: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [HooksComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(HooksComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads hooks on init', () => {
    const c = make();
    expect(svc.listHooks).toHaveBeenCalled();
    expect(c.hooks).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error on load failure', () => {
    svc.listHooks.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load hooks.');
  });

  it('toggleEvent adds then removes an event', () => {
    const c = make();
    c.openCreateModal();
    c.toggleEvent('KYC_APPROVED');
    expect(c.formEvents).toEqual(['KYC_APPROVED']);
    c.toggleEvent('KYC_APPROVED');
    expect(c.formEvents).toEqual([]);
  });

  describe('submitModal', () => {
    it('does nothing without name, url or events', () => {
      const c = make();
      c.openCreateModal();
      c.submitModal();
      expect(svc.createHook).not.toHaveBeenCalled();
    });
    it('creates a hook on success', () => {
      const c = make();
      c.openCreateModal();
      c.formName = 'New'; c.formUrl = 'https://y.com'; c.formEvents = ['ACCOUNT_OPENED'];
      c.submitModal();
      expect(svc.createHook).toHaveBeenCalledWith({ name: 'New', hookType: 'WEB', url: 'https://y.com', events: ['ACCOUNT_OPENED'] });
      expect(c.hooks).toHaveLength(2);
    });
    it('updates a hook in edit mode', () => {
      const c = make();
      c.openEditModal(hook());
      c.formName = 'Edited';
      c.submitModal();
      expect(svc.updateHook).toHaveBeenCalledWith('h1', expect.objectContaining({ name: 'Edited' }));
      expect(c.hooks.find(h => h.id === 'h1')!.name).toBe('Edited');
    });
    it('surfaces an error on failure', () => {
      svc.createHook.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.formName = 'New'; c.formUrl = 'https://y.com'; c.formEvents = ['ACCOUNT_OPENED'];
      c.submitModal();
      expect(c.modalError).toBe('Failed to save hook.');
    });
  });

  it('submitDelete removes the hook', () => {
    const c = make();
    c.openDeleteModal(hook());
    c.submitDelete();
    expect(svc.deleteHook).toHaveBeenCalledWith('h1');
    expect(c.hooks).toHaveLength(0);
  });

  it('openEditModal copies a fresh events array', () => {
    const c = make();
    const h = hook({ events: ['LOAN_APPROVED'] });
    c.openEditModal(h);
    c.formEvents.push('EXTRA');
    expect(h.events).toEqual(['LOAN_APPROVED']);
  });
});
