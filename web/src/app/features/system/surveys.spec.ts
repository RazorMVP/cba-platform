import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SurveysComponent } from './surveys';
import { SystemService, Survey } from './system.service';

type Svc = Record<
  'listSurveys' | 'createSurvey' | 'updateSurvey' | 'deleteSurvey',
  ReturnType<typeof vi.fn>
>;

function survey(over: Partial<Survey> = {}): Survey {
  return { id: 's1', name: 'PPI', key: 'ppi', countryCode: 'KE', description: 'desc', questions: [], ...over };
}

describe('SurveysComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listSurveys: vi.fn().mockReturnValue(of([survey()])),
      createSurvey: vi.fn().mockReturnValue(of(survey({ id: 's2' }))),
      updateSurvey: vi.fn().mockReturnValue(of(survey({ id: 's1' }))),
      deleteSurvey: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [SurveysComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SurveysComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads surveys on init', () => {
    const c = make();
    expect(svc.listSurveys).toHaveBeenCalled();
    expect(c.surveys).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listSurveys.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load surveys.');
  });

  it('toggleExpand toggles a single survey id', () => {
    const c = make();
    c.toggleExpand('s1');
    expect(c.expandedSurvey).toBe('s1');
    c.toggleExpand('s1');
    expect(c.expandedSurvey).toBeNull();
  });

  it('openEdit seeds the form, coalescing a null description', () => {
    const c = make();
    c.openEdit(survey({ id: 's1', name: 'X', key: 'x', countryCode: 'NG', description: null }));
    expect(c.editTarget?.id).toBe('s1');
    expect(c.form).toEqual({ name: 'X', key: 'x', countryCode: 'NG', description: '' });
    expect(c.activeModal).toBe('edit');
  });

  describe('save', () => {
    it('creates when no edit target then reloads', () => {
      const c = make();
      c.openCreate();
      c.form = { name: 'New', key: 'new', countryCode: 'KE', description: '' };
      c.save();
      expect(svc.createSurvey).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listSurveys).toHaveBeenCalledTimes(2);
    });

    it('updates when an edit target is set', () => {
      const c = make();
      c.openEdit(survey({ id: 's1' }));
      c.save();
      expect(svc.updateSurvey).toHaveBeenCalledWith('s1', c.form);
    });

    it('surfaces an error on failure', () => {
      svc.createSurvey.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('confirmDelete', () => {
    it('deletes the target then reloads', () => {
      const c = make();
      c.openDelete(survey({ id: 's1' }));
      c.confirmDelete();
      expect(svc.deleteSurvey).toHaveBeenCalledWith('s1');
      expect(svc.listSurveys).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.deleteTarget = null;
      c.confirmDelete();
      expect(svc.deleteSurvey).not.toHaveBeenCalled();
    });
  });
});
