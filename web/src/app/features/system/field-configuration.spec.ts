import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FieldConfigurationComponent } from './field-configuration';
import { SystemService, FieldConfiguration } from './system.service';

type Svc = Record<
  'listFieldConfigurations' | 'updateFieldConfig' | 'createFieldConfig' | 'deleteFieldConfig',
  ReturnType<typeof vi.fn>
>;

function field(over: Partial<FieldConfiguration> = {}): FieldConfiguration {
  return {
    id: 'f1', entityType: 'CLIENT', fieldName: 'middleName', fieldLabel: 'Middle Name',
    enabled: true, mandatory: false, displayOrder: 1, description: null, updatedAt: '2026-01-01', ...over,
  };
}

describe('FieldConfigurationComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFieldConfigurations: vi.fn().mockReturnValue(of([
        field({ id: 'f2', entityType: 'ADDRESS', fieldName: 'street', displayOrder: 2 }),
        field({ id: 'f1', entityType: 'CLIENT', fieldName: 'middleName', displayOrder: 1 }),
      ])),
      updateFieldConfig: vi.fn().mockReturnValue(of(field({ id: 'f1', fieldLabel: 'Edited' }))),
      createFieldConfig: vi.fn().mockReturnValue(of(field({ id: 'f3', entityType: 'LOAN', fieldName: 'purpose', displayOrder: 5 }))),
      deleteFieldConfig: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [FieldConfigurationComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FieldConfigurationComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads, sorts and groups field configs on init', () => {
    const c = make();
    expect(svc.listFieldConfigurations).toHaveBeenCalled();
    expect(c.all).toHaveLength(2);
    expect(c.all[0].displayOrder).toBe(1); // sorted by displayOrder
    expect(c.entityTypes).toEqual(['ADDRESS', 'CLIENT']); // distinct + sorted
    expect(c.activeEntity).toBe('ADDRESS'); // first
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listFieldConfigurations.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load field configurations.');
  });

  it('fields getter filters to the active entity', () => {
    const c = make();
    c.activeEntity = 'CLIENT';
    expect(c.fields).toHaveLength(1);
    expect(c.fields[0].entityType).toBe('CLIENT');
  });

  it('selectEntity switches the active tab and clears edit state', () => {
    const c = make();
    c.editingId = 'f1';
    c.selectEntity('CLIENT');
    expect(c.activeEntity).toBe('CLIENT');
    expect(c.editingId).toBe('');
  });

  describe('startEdit', () => {
    it('seeds the edit form, coalescing a null description', () => {
      const c = make();
      c.startEdit(field({ id: 'f1', fieldLabel: 'Middle Name', description: null }));
      expect(c.editingId).toBe('f1');
      expect(c.editForm.fieldLabel).toBe('Middle Name');
      expect(c.editForm.description).toBe('');
    });
  });

  describe('saveEdit', () => {
    it('updates the field and replaces it in the list', () => {
      const c = make();
      c.startEdit(field({ id: 'f1' }));
      c.saveEdit(field({ id: 'f1' }));
      expect(svc.updateFieldConfig).toHaveBeenCalledWith('f1', c.editForm);
      expect(c.all.find(f => f.id === 'f1')?.fieldLabel).toBe('Edited');
      expect(c.editingId).toBe('');
    });

    it('surfaces an error on failure', () => {
      svc.updateFieldConfig.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.saveEdit(field({ id: 'f1' }));
      expect(c.editError).toBe('Save failed.');
      expect(c.editWorking).toBe(false);
    });
  });

  describe('create flow', () => {
    it('openCreate seeds the form with the active entity', () => {
      const c = make();
      c.activeEntity = 'CLIENT';
      c.openCreate();
      expect(c.createForm.entityType).toBe('CLIENT');
      expect(c.showCreate).toBe(true);
    });

    it('submitCreate appends and registers a new entity type', () => {
      const c = make();
      c.openCreate();
      c.createForm = { entityType: 'LOAN', fieldName: 'purpose', fieldLabel: 'Purpose' };
      c.submitCreate();
      expect(svc.createFieldConfig).toHaveBeenCalledWith(c.createForm);
      expect(c.all.some(f => f.id === 'f3')).toBe(true);
      expect(c.entityTypes).toContain('LOAN');
      expect(c.showCreate).toBe(false);
    });

    it('surfaces an error on failure', () => {
      svc.createFieldConfig.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.submitCreate();
      expect(c.createError).toBe('Failed to create field.');
      expect(c.createWorking).toBe(false);
    });
  });

  describe('delete flow', () => {
    it('confirmDelete / cancelDelete toggle the deleting id', () => {
      const c = make();
      c.confirmDelete('f1');
      expect(c.deletingId).toBe('f1');
      c.cancelDelete();
      expect(c.deletingId).toBe('');
    });

    it('doDelete removes the field and re-derives entity types', () => {
      const c = make();
      c.confirmDelete('f1'); // the only CLIENT field
      c.doDelete();
      expect(svc.deleteFieldConfig).toHaveBeenCalledWith('f1');
      expect(c.all.some(f => f.id === 'f1')).toBe(false);
      expect(c.entityTypes).toEqual(['ADDRESS']);
      expect(c.deletingId).toBe('');
    });

    it('surfaces an error on failure', () => {
      svc.deleteFieldConfig.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.confirmDelete('f1');
      c.doDelete();
      expect(c.deleteWorking).toBe(false);
    });
  });
});
