import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DataTablesComponent } from './datatables';
import { SystemService, DataTable } from './system.service';

type Svc = Record<'listDataTables' | 'createDataTable' | 'deleteDataTable', ReturnType<typeof vi.fn>>;

function table(over: Partial<DataTable> = {}): DataTable {
  return {
    registeredTableName: 'm_extra', applicationTableName: 'm_client', allowMultipleRows: false,
    columns: [{ columnName: 'note', columnType: 'STRING', columnLength: 100, nullable: true, unique: false, codeId: null }],
    ...over,
  };
}

describe('DataTablesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listDataTables: vi.fn().mockReturnValue(of([table()])),
      createDataTable: vi.fn().mockReturnValue(of(table({ registeredTableName: 'm_new' }))),
      deleteDataTable: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [DataTablesComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(DataTablesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads tables on init', () => {
    const c = make();
    expect(svc.listDataTables).toHaveBeenCalled();
    expect(c.tables).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listDataTables.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load data tables.');
  });

  it('toggleExpand toggles a single table name', () => {
    const c = make();
    c.toggleExpand('m_extra');
    expect(c.expandedTable).toBe('m_extra');
    c.toggleExpand('m_extra');
    expect(c.expandedTable).toBeNull();
  });

  describe('column builder', () => {
    it('openCreate seeds one default column', () => {
      const c = make();
      c.openCreate();
      expect(c.form.columns).toHaveLength(1);
      expect(c.activeModal).toBe('create');
    });

    it('addColumn / removeColumn adjust the column list', () => {
      const c = make();
      c.openCreate();
      c.addColumn();
      expect(c.form.columns).toHaveLength(2);
      c.removeColumn(0);
      expect(c.form.columns).toHaveLength(1);
    });
  });

  describe('canSave', () => {
    it('is false when names or column names are blank', () => {
      const c = make();
      c.openCreate();
      expect(c.canSave).toBe(false); // blank table + column names
      c.form.registeredTableName = 'm_x';
      c.form.applicationTableName = 'm_client';
      expect(c.canSave).toBe(false); // column name still blank
      c.form.columns[0].columnName = 'field';
      expect(c.canSave).toBe(true);
    });

    it('is false when there are no columns', () => {
      const c = make();
      c.openCreate();
      c.form.registeredTableName = 'm_x';
      c.form.applicationTableName = 'm_client';
      c.form.columns = [];
      expect(c.canSave).toBe(false);
    });
  });

  describe('save', () => {
    it('creates the table then reloads', () => {
      const c = make();
      c.openCreate();
      c.form.registeredTableName = 'm_x';
      c.form.applicationTableName = 'm_client';
      c.form.columns[0].columnName = 'field';
      c.save();
      expect(svc.createDataTable).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listDataTables).toHaveBeenCalledTimes(2);
    });

    it('surfaces an error on failure', () => {
      svc.createDataTable.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('confirmDelete', () => {
    it('deletes by registered table name then reloads', () => {
      const c = make();
      c.openDelete(table({ registeredTableName: 'm_extra' }));
      c.confirmDelete();
      expect(svc.deleteDataTable).toHaveBeenCalledWith('m_extra');
      expect(svc.listDataTables).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.deleteTarget = null;
      c.confirmDelete();
      expect(svc.deleteDataTable).not.toHaveBeenCalled();
    });
  });
});
