import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { GlobalConfigComponent } from './global-config';
import { SystemService, GlobalConfig } from './system.service';

type Svc = Record<'listConfigurations' | 'updateConfiguration', ReturnType<typeof vi.fn>>;

function cfg(over: Partial<GlobalConfig> = {}): GlobalConfig {
  return {
    id: 'cfg1', name: 'maker-checker', enabled: true,
    stringValue: null, numericValue: null, booleanValue: null, description: 'd', ...over,
  };
}

describe('GlobalConfigComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listConfigurations: vi.fn().mockReturnValue(of([cfg()])),
      updateConfiguration: vi.fn().mockReturnValue(of(cfg({ enabled: false }))),
    };
    TestBed.configureTestingModule({
      imports: [GlobalConfigComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(GlobalConfigComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads configurations on init', () => {
    const c = make();
    expect(svc.listConfigurations).toHaveBeenCalled();
    expect(c.configs).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listConfigurations.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load configuration.');
  });

  it('filtered narrows by name', () => {
    const c = make();
    c.configs = [cfg({ id: 'a', name: 'alpha' }), cfg({ id: 'b', name: 'beta' })];
    c.searchQuery = 'BET';
    expect(c.filtered).toHaveLength(1);
  });

  describe('startEdit', () => {
    it('seeds the edit form from the config, coalescing nulls', () => {
      const c = make();
      c.startEdit(cfg({ id: 'cfg1', enabled: true, stringValue: 'on', numericValue: null }));
      expect(c.editingId).toBe('cfg1');
      expect(c.editForm.enabled).toBe(true);
      expect(c.editForm.stringValue).toBe('on');
      expect(c.editForm.numericValue).toBeUndefined();
    });
  });

  it('cancelEdit clears the editing id', () => {
    const c = make();
    c.editingId = 'cfg1';
    c.cancelEdit();
    expect(c.editingId).toBe('');
  });

  describe('saveEdit', () => {
    it('updates the config and replaces it in the list', () => {
      const c = make();
      c.configs = [cfg({ id: 'cfg1', enabled: true })];
      c.startEdit(c.configs[0]);
      c.saveEdit(c.configs[0]);
      expect(svc.updateConfiguration).toHaveBeenCalledWith('cfg1', c.editForm);
      expect(c.configs[0].enabled).toBe(false);
      expect(c.editingId).toBe('');
    });

    it('surfaces an error on failure', () => {
      svc.updateConfiguration.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.saveEdit(cfg());
      expect(c.editError).toBe('Failed to save.');
      expect(c.editWorking).toBe(false);
    });
  });

  describe('valueDisplay', () => {
    it('shows the right value by precedence', () => {
      const c = make();
      expect(c.valueDisplay(cfg({ stringValue: 'hi' }))).toBe('hi');
      expect(c.valueDisplay(cfg({ stringValue: '' }))).toBe('—');
      expect(c.valueDisplay(cfg({ numericValue: 42 }))).toBe('42');
      expect(c.valueDisplay(cfg({ booleanValue: true }))).toBe('true');
      expect(c.valueDisplay(cfg({ booleanValue: false }))).toBe('false');
      expect(c.valueDisplay(cfg())).toBe('—');
    });
  });

  describe('valueType', () => {
    it('classifies the populated field', () => {
      const c = make();
      expect(c.valueType(cfg({ stringValue: 'x' }))).toBe('string');
      expect(c.valueType(cfg({ numericValue: 1 }))).toBe('number');
      expect(c.valueType(cfg({ booleanValue: false }))).toBe('boolean');
      expect(c.valueType(cfg())).toBe('none');
    });
  });
});
