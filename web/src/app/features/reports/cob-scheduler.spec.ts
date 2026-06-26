import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CobSchedulerComponent } from './cob-scheduler';
import { ReportService, CobJob, CobJobHistory } from './report.service';

type Svc = Record<'listJobs' | 'runJob' | 'getJobHistory', ReturnType<typeof vi.fn>>;

function job(over: Partial<CobJob> = {}): CobJob {
  return {
    jobName: 'interestAccrualJob', displayName: 'Interest Accrual',
    active: true, currentlyRunning: false, previousRunStatus: 'SUCCESS', ...over,
  };
}
function hist(over: Partial<CobJobHistory> = {}): CobJobHistory {
  return {
    id: 'h1', jobName: 'interestAccrualJob', startTime: '2026-06-01T00:00:00Z',
    endTime: '2026-06-01T00:00:02Z', status: 'SUCCESS', ...over,
  };
}

describe('CobSchedulerComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    vi.useFakeTimers();
    svc = {
      listJobs: vi.fn().mockReturnValue(of([job()])),
      runJob: vi.fn().mockReturnValue(of(void 0)),
      getJobHistory: vi.fn().mockReturnValue(of([hist()])),
    };
    TestBed.configureTestingModule({
      imports: [CobSchedulerComponent],
      providers: [{ provide: ReportService, useValue: svc }],
    });
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  function make() {
    const fixture = TestBed.createComponent(CobSchedulerComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads jobs on init', () => {
    const c = make();
    expect(svc.listJobs).toHaveBeenCalled();
    expect(c.jobs).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listJobs.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load CoB jobs.');
    expect(c.loading).toBe(false);
  });

  describe('runJob', () => {
    it('triggers the job and schedules a refresh', () => {
      const c = make();
      c.runJob(job());
      expect(svc.runJob).toHaveBeenCalledWith('interestAccrualJob');
      expect(c.runningJobs.has('interestAccrualJob')).toBe(false);
      // refresh timer fires a second listJobs after the delay
      vi.advanceTimersByTime(2600);
      expect(svc.listJobs).toHaveBeenCalledTimes(2);
    });

    it('reloads history when the run job is the selected job', () => {
      const c = make();
      c.selectedJob = job();
      c.runJob(job());
      expect(svc.getJobHistory).toHaveBeenCalledWith('interestAccrualJob');
    });

    it('records a per-job error on failure', () => {
      svc.runJob.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.runJob(job());
      expect(c.runError(job())).toBe('Failed to trigger job.');
      expect(c.runningJobs.has('interestAccrualJob')).toBe(false);
    });
  });

  describe('selectJob', () => {
    it('selects a job and loads its history', () => {
      const c = make();
      c.selectJob(job());
      expect(c.selectedJob?.jobName).toBe('interestAccrualJob');
      expect(svc.getJobHistory).toHaveBeenCalledWith('interestAccrualJob');
      expect(c.history).toHaveLength(1);
    });

    it('toggles off when the same job is selected again', () => {
      const c = make();
      c.selectJob(job());
      c.selectJob(job());
      expect(c.selectedJob).toBeNull();
      expect(c.history).toEqual([]);
    });
  });

  it('isRunning reflects local + server state', () => {
    const c = make();
    expect(c.isRunning(job({ currentlyRunning: true }))).toBe(true);
    c.runningJobs.add('interestAccrualJob');
    expect(c.isRunning(job())).toBe(true);
    c.runningJobs.clear();
    expect(c.isRunning(job())).toBe(false);
  });

  it('statusVariant maps run statuses', () => {
    const c = make();
    expect(c.statusVariant(null)).toBe('neutral');
    expect(c.statusVariant('SUCCESS')).toBe('success');
    expect(c.statusVariant('FAILED')).toBe('error');
    expect(c.statusVariant('RUNNING')).toBe('warning');
  });

  it('duration formats elapsed time', () => {
    const c = make();
    expect(c.duration(hist({ endTime: undefined }))).toBe('Running…');
    expect(c.duration(hist({ startTime: '2026-06-01T00:00:00Z', endTime: '2026-06-01T00:00:00.500Z' }))).toBe('500ms');
    expect(c.duration(hist({ startTime: '2026-06-01T00:00:00Z', endTime: '2026-06-01T00:00:05Z' }))).toBe('5.0s');
    expect(c.duration(hist({ startTime: '2026-06-01T00:00:00Z', endTime: '2026-06-01T00:01:30Z' }))).toBe('1m 30s');
  });
});
