import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CodesComponent } from './codes';
import { SystemService, Code, CodeValue } from './system.service';

type Svc = Record<
  'listCodes' | 'createCode' | 'deleteCode' | 'listCodeValues' |
  'createCodeValue' | 'updateCodeValue' | 'deleteCodeValue',
  ReturnType<typeof vi.fn>
>;

function value(over: Partial<CodeValue> = {}): CodeValue {
  return { id: 'v1', name: 'Val', description: 'desc', position: 1, active: true, ...over };
}
function code(over: Partial<Code> = {}): Code {
  return { id: 'cd1', name: 'Gender', systemDefined: false, codeValues: [], ...over };
}

describe('CodesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listCodes: vi.fn().mockReturnValue(of([code()])),
      createCode: vi.fn().mockReturnValue(of(code({ id: 'cd2', name: 'Status' }))),
      deleteCode: vi.fn().mockReturnValue(of(void 0)),
      listCodeValues: vi.fn().mockReturnValue(of([value()])),
      createCodeValue: vi.fn().mockReturnValue(of(value({ id: 'v2', name: 'New' }))),
      updateCodeValue: vi.fn().mockReturnValue(of(value({ id: 'v1', name: 'Edited' }))),
      deleteCodeValue: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [CodesComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CodesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads codes on init', () => {
    const c = make();
    expect(svc.listCodes).toHaveBeenCalled();
    expect(c.codes).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listCodes.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load codes.');
    expect(c.loading).toBe(false);
  });

  it('filtered narrows by name (case-insensitive)', () => {
    const c = make();
    c.codes = [code({ id: 'a', name: 'Alpha' }), code({ id: 'b', name: 'Beta' })];
    c.searchQuery = 'alp';
    expect(c.filtered).toHaveLength(1);
    expect(c.filtered[0].name).toBe('Alpha');
    c.searchQuery = '';
    expect(c.filtered).toHaveLength(2);
  });

  describe('toggleExpand', () => {
    it('collapses when the same code is toggled again', () => {
      const c = make();
      c.toggleExpand('cd1');
      expect(c.expandedCodeId).toBe('cd1');
      c.toggleExpand('cd1');
      expect(c.expandedCodeId).toBe('');
    });

    it('lazy-loads values when expanding a code with none', () => {
      const c = make();
      c.codes = [code({ id: 'cd1', codeValues: [] })];
      c.toggleExpand('cd1');
      expect(svc.listCodeValues).toHaveBeenCalledWith('cd1');
      expect(c.codes[0].codeValues).toHaveLength(1);
    });

    it('does not reload when values already present', () => {
      const c = make();
      c.codes = [code({ id: 'cd1', codeValues: [value()] })];
      c.toggleExpand('cd1');
      expect(svc.listCodeValues).not.toHaveBeenCalled();
    });
  });

  describe('submitValue', () => {
    it('does nothing without a name', () => {
      const c = make();
      c.openAddValue('cd1');
      c.valueForm.name = '';
      c.submitValue();
      expect(svc.createCodeValue).not.toHaveBeenCalled();
    });

    it('creates a value and appends it', () => {
      const c = make();
      c.codes = [code({ id: 'cd1', codeValues: [] })];
      c.openAddValue('cd1');
      c.valueForm = { name: 'New', description: 'd', position: 2 };
      c.submitValue();
      expect(svc.createCodeValue).toHaveBeenCalledWith('cd1', { name: 'New', description: 'd', position: 2 });
      expect(c.codes[0].codeValues).toHaveLength(1);
      expect(c.addingValueForCodeId).toBe('');
    });

    it('updates an existing value in place', () => {
      const c = make();
      c.codes = [code({ id: 'cd1', codeValues: [value({ id: 'v1' })] })];
      c.openEditValue('cd1', value({ id: 'v1' }));
      c.submitValue();
      expect(svc.updateCodeValue).toHaveBeenCalledWith('cd1', 'v1', expect.anything());
      expect(c.codes[0].codeValues[0].name).toBe('Edited');
    });

    it('surfaces an error on failure', () => {
      svc.createCodeValue.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openAddValue('cd1');
      c.valueForm = { name: 'New', description: '', position: 0 };
      c.submitValue();
      expect(c.valueError).toBe('Failed to save value.');
      expect(c.valueWorking).toBe(false);
    });
  });

  it('deleteValue removes the value from the code', () => {
    const c = make();
    c.codes = [code({ id: 'cd1', codeValues: [value({ id: 'v1' })] })];
    c.deleteValue('cd1', 'v1');
    expect(svc.deleteCodeValue).toHaveBeenCalledWith('cd1', 'v1');
    expect(c.codes[0].codeValues).toHaveLength(0);
  });

  describe('submitCreateCode', () => {
    it('does nothing without a name', () => {
      const c = make();
      c.newCodeName = '';
      c.submitCreateCode();
      expect(svc.createCode).not.toHaveBeenCalled();
    });

    it('creates a code and appends it with empty values', () => {
      const c = make();
      c.newCodeName = 'Status';
      c.submitCreateCode();
      expect(svc.createCode).toHaveBeenCalledWith({ name: 'Status' });
      expect(c.codes).toHaveLength(2);
      expect(c.createCodeModal).toBe(false);
    });

    it('surfaces an error on failure', () => {
      svc.createCode.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.newCodeName = 'X';
      c.submitCreateCode();
      expect(c.codeError).toBe('Failed to create code.');
      expect(c.codeWorking).toBe(false);
    });
  });

  describe('deleteCode', () => {
    it('refuses to delete a system-defined code', () => {
      const c = make();
      c.deleteCode(code({ systemDefined: true }));
      expect(svc.deleteCode).not.toHaveBeenCalled();
    });

    it('removes a user-defined code', () => {
      const c = make();
      c.codes = [code({ id: 'cd1' })];
      c.deleteCode(code({ id: 'cd1', systemDefined: false }));
      expect(svc.deleteCode).toHaveBeenCalledWith('cd1');
      expect(c.codes).toHaveLength(0);
    });
  });
});
