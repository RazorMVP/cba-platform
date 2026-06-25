import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApiKeysComponent } from './api-keys';
import { CardsService, ApiKey, IssueApiKeyResponse } from '../cards.service';

type Svc = Record<'listApiKeys' | 'issueApiKey' | 'revokeApiKey', ReturnType<typeof vi.fn>>;

function key(over: Partial<ApiKey> = {}): ApiKey {
  return {
    id: 'k1', name: 'Default', keyHash: 'hash', active: true,
    scopes: ['cards:read'], tier: 'BASIC', lastUsedAt: null, createdAt: '2026-01-01', ...over,
  };
}
const issued: IssueApiKeyResponse = { id: 'k2', name: 'New', key: 'cba_secret', scopes: [], createdAt: '2026-01-01' };

describe('ApiKeysComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listApiKeys: vi.fn().mockReturnValue(of([key()])),
      issueApiKey: vi.fn().mockReturnValue(of(issued)),
      revokeApiKey: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [ApiKeysComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ApiKeysComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads keys on init', () => {
    const c = make();
    expect(svc.listApiKeys).toHaveBeenCalled();
    expect(c.keys).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false on error', () => {
    svc.listApiKeys.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('openCreate resets the form and clears any prior reveal', () => {
    const c = make();
    c.newKeyResult = issued;
    c.openCreate();
    expect(c.showModal).toBe(true);
    expect(c.newKeyResult).toBeNull();
    expect(c.form).toEqual({ name: '', scopes: [], tier: 'BASIC' });
  });

  describe('scope toggling', () => {
    it('toggleScope adds then removes a scope', () => {
      const c = make();
      c.toggleScope('cards:write');
      expect(c.form.scopes).toEqual(['cards:write']);
      expect(c.hasScope('cards:write')).toBe(true);
      c.toggleScope('cards:write');
      expect(c.form.scopes).toEqual([]);
      expect(c.hasScope('cards:write')).toBe(false);
    });
  });

  it('submit issues a key and reveals it (modal stays open)', () => {
    const c = make();
    c.openCreate();
    c.form = { name: 'New', scopes: ['cards:read'], tier: 'PRO' };
    c.submit();
    expect(svc.issueApiKey).toHaveBeenCalledWith(c.form);
    expect(c.newKeyResult).toEqual(issued);
    expect(c.showModal).toBe(true);
  });

  it('closeModal reloads only after the key was copied', () => {
    const c = make();
    svc.listApiKeys.mockClear();
    c.copiedKey = false;
    c.closeModal();
    expect(svc.listApiKeys).not.toHaveBeenCalled();

    c.copiedKey = true;
    c.closeModal();
    expect(svc.listApiKeys).toHaveBeenCalled();
    expect(c.copiedKey).toBe(false);
  });

  it('revoke removes the key by reloading', () => {
    const c = make();
    svc.listApiKeys.mockClear();
    c.revoke('k1');
    expect(svc.revokeApiKey).toHaveBeenCalledWith('k1');
    expect(svc.listApiKeys).toHaveBeenCalled();
  });

  it('formatDate falls back to a dash for null', () => {
    const c = make();
    expect(c.formatDate(null)).toBe('—');
    expect(c.formatDate('2026-01-01')).not.toBe('—');
  });
});
