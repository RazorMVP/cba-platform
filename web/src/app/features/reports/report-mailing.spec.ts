import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportMailingComponent } from './report-mailing';
import { ReportService, ReportMailingJob, Report } from './report.service';

type Svc = Record<
  'listMailingJobs' | 'listReports' | 'createMailingJob' | 'updateMailingJob' |
  'deleteMailingJob' | 'runMailingJob',
  ReturnType<typeof vi.fn>
>;

function mjob(over: Partial<ReportMailingJob> = {}): ReportMailingJob {
  return {
    id: 'm1', name: 'Daily Loans', reportName: 'Active Loans', emailRecipients: 'a@b.com',
    emailSubject: 'Report', recurrence: 'FREQ=DAILY', outputType: 'CSV', isActive: true,
    runCount: 0, ...over,
  };
}
const report: Report = {
  id: 'r1', reportName: 'Active Loans', reportType: 'Table', coreReport: false,
  useReport: true, reportParameters: [],
};

describe('ReportMailingComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listMailingJobs: vi.fn().mockReturnValue(of([mjob()])),
      listReports: vi.fn().mockReturnValue(of([report])),
      createMailingJob: vi.fn().mockReturnValue(of(mjob({ id: 'm2', name: 'New Job' }))),
      updateMailingJob: vi.fn().mockReturnValue(of(mjob({ id: 'm1', name: 'Edited' }))),
      deleteMailingJob: vi.fn().mockReturnValue(of(void 0)),
      runMailingJob: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [ReportMailingComponent],
      providers: [{ provide: ReportService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ReportMailingComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads mailing jobs + reports on init', () => {
    const c = make();
    expect(svc.listMailingJobs).toHaveBeenCalled();
    expect(svc.listReports).toHaveBeenCalled();
    expect(c.mailingJobs).toHaveLength(1);
    expect(c.reports).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listMailingJobs.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load mailing jobs.');
    expect(c.loading).toBe(false);
  });

  describe('runNow', () => {
    it('triggers the job and reloads', () => {
      const c = make();
      c.runNow(mjob());
      expect(svc.runMailingJob).toHaveBeenCalledWith('m1');
      expect(c.isRunning(mjob())).toBe(false);
      expect(svc.listMailingJobs).toHaveBeenCalledTimes(2);
    });

    it('records a per-job error on failure', () => {
      svc.runMailingJob.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.runNow(mjob());
      expect(c.runError(mjob())).toBe('Failed to trigger mailing job.');
    });
  });

  describe('create/edit modal', () => {
    it('openCreateModal defaults the report and recurrence', () => {
      const c = make();
      c.openCreateModal();
      expect(c.activeModal).toBe('create');
      expect(c.formReportName).toBe('Active Loans');
      expect(c.rrulePreset).toBe('FREQ=DAILY');
    });

    it('openEditModal seeds the form and matches a preset', () => {
      const c = make();
      c.openEditModal(mjob({ id: 'm9', name: 'Weekly', recurrence: 'FREQ=WEEKLY;BYDAY=MO' }));
      expect(c.editingId).toBe('m9');
      expect(c.formName).toBe('Weekly');
      expect(c.rrulePreset).toBe('FREQ=WEEKLY;BYDAY=MO');
    });

    it('onRrulePresetChange syncs the recurrence', () => {
      const c = make();
      c.rrulePreset = 'FREQ=WEEKLY;BYDAY=MO';
      c.onRrulePresetChange();
      expect(c.formRecurrence).toBe('FREQ=WEEKLY;BYDAY=MO');
    });

    it('effectiveRrule prefers the preset, else the custom recurrence', () => {
      const c = make();
      c.rrulePreset = 'FREQ=DAILY';
      expect(c.effectiveRrule).toBe('FREQ=DAILY');
      c.rrulePreset = '';
      c.formRecurrence = 'FREQ=MONTHLY;BYMONTHDAY=15';
      expect(c.effectiveRrule).toBe('FREQ=MONTHLY;BYMONTHDAY=15');
    });

    it('submitModal is a no-op when required fields missing', () => {
      const c = make();
      c.openCreateModal();
      c.formName = '';
      c.submitModal();
      expect(svc.createMailingJob).not.toHaveBeenCalled();
    });

    it('submitModal creates and appends the job', () => {
      const c = make();
      c.openCreateModal();
      c.formName = 'New Job';
      c.formReportName = 'Active Loans';
      c.formRecipients = 'a@b.com';
      c.formSubject = 'Subj';
      c.submitModal();
      expect(svc.createMailingJob).toHaveBeenCalled();
      expect(c.mailingJobs).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });

    it('submitModal updates an existing job in place', () => {
      const c = make();
      c.mailingJobs = [mjob({ id: 'm1', name: 'Old' })];
      c.openEditModal(mjob({ id: 'm1' }));
      c.formName = 'Edited';
      c.formReportName = 'Active Loans';
      c.formRecipients = 'a@b.com';
      c.formSubject = 'Subj';
      c.submitModal();
      expect(svc.updateMailingJob).toHaveBeenCalledWith('m1', expect.anything());
      expect(c.mailingJobs[0].name).toBe('Edited');
    });

    it('submitModal surfaces a save error', () => {
      svc.createMailingJob.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.formName = 'New Job';
      c.formReportName = 'Active Loans';
      c.formRecipients = 'a@b.com';
      c.formSubject = 'Subj';
      c.submitModal();
      expect(c.modalError).toBe('Failed to save mailing job.');
      expect(c.modalWorking).toBe(false);
    });
  });

  describe('delete modal', () => {
    it('submitDelete removes the job', () => {
      const c = make();
      c.mailingJobs = [mjob({ id: 'm1' })];
      c.openDeleteModal(mjob({ id: 'm1' }));
      c.submitDelete();
      expect(svc.deleteMailingJob).toHaveBeenCalledWith('m1');
      expect(c.mailingJobs).toHaveLength(0);
    });
  });

  it('rruleLabel maps known presets to labels', () => {
    const c = make();
    expect(c.rruleLabel('FREQ=DAILY')).toBe('Daily');
    expect(c.rruleLabel('FREQ=WEEKLY;BYDAY=MO')).toBe('Weekly (Mon)');
    expect(c.rruleLabel('FREQ=YEARLY')).toBe('FREQ=YEARLY');
  });

  it('statusVariant maps active flag', () => {
    const c = make();
    expect(c.statusVariant(true)).toBe('success');
    expect(c.statusVariant(false)).toBe('neutral');
  });
});
