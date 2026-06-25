import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HolidaysComponent } from './holidays';
import { SystemService, Holiday } from './system.service';

type Svc = Record<
  'listHolidays' | 'createHoliday' | 'activateHoliday' | 'deleteHoliday',
  ReturnType<typeof vi.fn>
>;

function holiday(over: Partial<Holiday> = {}): Holiday {
  return {
    id: 'h1', name: 'New Year', fromDate: '2026-01-01', toDate: '2026-01-01',
    repaymentSchedulingType: 'NEXT_WORKING_DAY', status: 'PENDING', processed: false,
    createdAt: '2026-01-01', ...over,
  };
}
function page(content: Holiday[], totalElements: number) {
  return of({ content, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)), size: 20, number: 0 });
}

describe('HolidaysComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listHolidays: vi.fn().mockReturnValue(page([holiday()], 1)),
      createHoliday: vi.fn().mockReturnValue(of(holiday({ id: 'h2' }))),
      activateHoliday: vi.fn().mockReturnValue(of(holiday({ id: 'h1', status: 'ACTIVE' }))),
      deleteHoliday: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [HolidaysComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(HolidaysComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the first page on init', () => {
    const c = make();
    expect(svc.listHolidays).toHaveBeenCalledWith(0);
    expect(c.holidays).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listHolidays.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load holidays.');
  });

  describe('submitCreate', () => {
    it('validates required name and dates', () => {
      const c = make();
      c.openCreate();
      c.formName = '';
      c.submitCreate();
      expect(svc.createHoliday).not.toHaveBeenCalled();
    });

    it('creates a holiday and reloads', () => {
      const c = make();
      c.openCreate();
      c.formName = 'Labour Day';
      c.formFrom = '2026-05-01';
      c.formTo = '2026-05-01';
      c.submitCreate();
      expect(svc.createHoliday).toHaveBeenCalledWith(expect.objectContaining({
        name: 'Labour Day', fromDate: '2026-05-01', toDate: '2026-05-01',
        repaymentSchedulingType: 'NEXT_WORKING_DAY',
      }));
      expect(c.activeModal).toBeNull();
      expect(svc.listHolidays).toHaveBeenCalledTimes(2);
    });

    it('surfaces an error on failure', () => {
      svc.createHoliday.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.formName = 'X';
      c.formFrom = '2026-05-01';
      c.formTo = '2026-05-01';
      c.submitCreate();
      expect(c.modalError).toBe('Failed to create holiday.');
      expect(c.modalWorking).toBe(false);
    });
  });

  describe('submitActivate', () => {
    it('replaces the activated holiday in the list', () => {
      const c = make();
      c.holidays = [holiday({ id: 'h1', status: 'PENDING' })];
      c.openActivate(c.holidays[0]);
      c.submitActivate();
      expect(svc.activateHoliday).toHaveBeenCalledWith('h1');
      expect(c.holidays[0].status).toBe('ACTIVE');
      expect(c.activeModal).toBeNull();
    });

    it('does nothing without a target id', () => {
      const c = make();
      c.targetId = null;
      c.submitActivate();
      expect(svc.activateHoliday).not.toHaveBeenCalled();
    });
  });

  it('submitDelete deletes then reloads', () => {
    const c = make();
    c.openDelete(holiday({ id: 'h1' }));
    c.submitDelete();
    expect(svc.deleteHoliday).toHaveBeenCalledWith('h1');
    expect(svc.listHolidays).toHaveBeenCalledTimes(2);
  });

  it('closeModal respects the working flag', () => {
    const c = make();
    c.activeModal = 'create';
    c.modalWorking = true;
    c.closeModal();
    expect(c.activeModal).toBe('create');
    c.modalWorking = false;
    c.closeModal();
    expect(c.activeModal).toBeNull();
  });

  describe('helpers', () => {
    it('targetName resolves the targeted holiday name', () => {
      const c = make();
      c.holidays = [holiday({ id: 'h1', name: 'NY' })];
      c.targetId = 'h1';
      expect(c.targetName()).toBe('NY');
      c.targetId = 'missing';
      expect(c.targetName()).toBe('');
    });

    it('schedulingLabel maps the type to a label', () => {
      const c = make();
      expect(c.schedulingLabel('NEXT_WORKING_DAY')).toBe('Next Working Day');
    });

    it('statusVariant maps ACTIVE/PENDING', () => {
      const c = make();
      expect(c.statusVariant('ACTIVE')).toBe('success');
      expect(c.statusVariant('PENDING')).toBe('warning');
    });
  });

  describe('pagination', () => {
    let c: HolidaysComponent;
    beforeEach(() => {
      // loadPage re-reads total from the service — echo the same total to keep bounds stable.
      svc.listHolidays.mockReturnValue(page([], 50));
      c = TestBed.createComponent(HolidaysComponent).componentInstance;
      c.total = 50; // 3 pages at size 20
    });

    it('nextPage advances but never past the last page', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
    });

    it('prevPage decrements but never below 0', () => {
      c.page = 1; c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });

    it('row-window getters compute the visible range', () => {
      c.page = 0;
      expect(c.totalPages).toBe(3);
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(20);
      c.page = 2;
      expect(c.startRow).toBe(41);
      expect(c.endRow).toBe(50);
    });

    it('startRow is 0 when there are no rows', () => {
      c.total = 0;
      expect(c.startRow).toBe(0);
    });
  });
});
