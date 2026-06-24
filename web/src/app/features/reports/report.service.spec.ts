import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { ReportService } from './report.service';

describe('ReportService', () => {
  let service: ReportService;
  let api: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    put: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    getPage: ReturnType<typeof vi.fn>;
    command: ReturnType<typeof vi.fn>;
    base: string;
  };

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      command: vi.fn().mockReturnValue(of({})),
      base: 'http://localhost:8080/api/v1',
    };
    TestBed.configureTestingModule({
      providers: [ReportService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(ReportService);
  });

  it('report CRUD routes correctly', () => {
    service.listReports().subscribe();
    expect(api.get).toHaveBeenCalledWith('/reports');
    service.getReport('r1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/reports/r1');
    service.deleteReport('r1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/reports/r1');
  });

  it('runReport URL-encodes the report name and passes params', () => {
    service.runReport('Active Loans', { branch: '001' }).subscribe();
    expect(api.get).toHaveBeenCalledWith('/runreports/Active%20Loans', { branch: '001' });
  });

  it('getExportUrl builds a fully-qualified download URL with the format', () => {
    const url = service.getExportUrl('Active Loans', 'csv', { branch: '001' });
    expect(url).toContain('http://localhost:8080/api/v1/runreports/Active%20Loans/export?');
    expect(url).toContain('branch=001');
    expect(url).toContain('format=csv');
  });

  it('CoB jobs URL-encode the job name', () => {
    service.runJob('interest accrual').subscribe();
    expect(api.post).toHaveBeenCalledWith('/jobs/interest%20accrual/run', {});
    service.getJobHistory('interest accrual').subscribe();
    expect(api.get).toHaveBeenCalledWith('/jobs/interest%20accrual/history');
  });

  it('listMailingJobs pages and unwraps content', () => {
    api.getPage.mockReturnValue(of({ content: [{ id: 'm1' }] }));
    let jobs: unknown;
    service.listMailingJobs().subscribe(j => (jobs = j));
    expect(api.getPage).toHaveBeenCalledWith('/reportmailingjobs');
    expect(jobs).toEqual([{ id: 'm1' }]);
  });

  it('runMailingJob uses the command pattern', () => {
    service.runMailingJob('m1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/reportmailingjobs/m1', 'run');
  });
});
