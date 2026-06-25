import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OfficesComponent } from './offices';
import { AdminService, Office } from './admin.service';

type Svc = Record<'listOffices' | 'createOffice' | 'updateOffice', ReturnType<typeof vi.fn>>;

function office(over: Partial<Office> = {}): Office {
  return {
    id: 'o1', name: 'HQ', externalId: 'EXT1', openingDate: '2020-01-01',
    parentId: null, parentName: null, hierarchy: '.', ...over,
  };
}

describe('OfficesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listOffices: vi.fn().mockReturnValue(of([office()])),
      createOffice: vi.fn().mockReturnValue(of(office({ id: 'o2', name: 'Branch' }))),
      updateOffice: vi.fn().mockReturnValue(of(office({ name: 'HQ-Edited' }))),
    };
    TestBed.configureTestingModule({
      imports: [OfficesComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(OfficesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads offices on init', () => {
    const c = make();
    expect(svc.listOffices).toHaveBeenCalled();
    expect(c.offices).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error on load failure', () => {
    svc.listOffices.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load offices.');
  });

  it('filtered matches by name', () => {
    const c = make();
    c.offices = [office({ name: 'Alpha' }), office({ id: 'o2', name: 'Beta' })];
    c.searchQuery = 'alp';
    expect(c.filtered).toHaveLength(1);
    c.searchQuery = '';
    expect(c.filtered).toHaveLength(2);
  });

  it('parentOffices excludes the office being edited', () => {
    const c = make();
    c.offices = [office({ id: 'o1' }), office({ id: 'o2' })];
    c.editingId = 'o1';
    expect(c.parentOffices.map(o => o.id)).toEqual(['o2']);
  });

  describe('submitModal', () => {
    it('creates in create mode and strips empty parentId', () => {
      const c = make();
      c.openCreateModal();
      c.form = { name: 'Branch', externalId: '', openingDate: '2021-01-01', parentId: '' };
      c.submitModal();
      expect(svc.createOffice).toHaveBeenCalledWith(expect.objectContaining({ name: 'Branch', parentId: undefined }));
      expect(c.offices).toHaveLength(2);
    });
    it('updates in edit mode', () => {
      const c = make();
      c.openEditModal(office());
      c.form.name = 'HQ-Edited';
      c.submitModal();
      expect(svc.updateOffice).toHaveBeenCalledWith('o1', expect.objectContaining({ name: 'HQ-Edited' }));
      expect(c.offices.find(o => o.id === 'o1')!.name).toBe('HQ-Edited');
    });
    it('does nothing without name or opening date', () => {
      const c = make();
      c.openCreateModal();
      c.submitModal();
      expect(svc.createOffice).not.toHaveBeenCalled();
    });
    it('surfaces an error on failure', () => {
      svc.createOffice.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.form = { name: 'Branch', externalId: '', openingDate: '2021-01-01', parentId: '' };
      c.submitModal();
      expect(c.modalError).toBe('Failed to save office.');
    });
  });

  it('parentName falls back to a dash', () => {
    const c = make();
    expect(c.parentName(office({ parentName: 'HQ' }))).toBe('HQ');
    expect(c.parentName(office({ parentName: null }))).toBe('—');
  });
});
