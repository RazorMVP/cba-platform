import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
      providers: [ReportService, { provide: ApiService, useValue: api }, provideHttpClient(), provideHttpClientTesting()],
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

  it('runReport GETs the run URL with the params and reads X-Report-Truncated', () => {
    const http = TestBed.inject(HttpTestingController);
    let result: unknown;
    service.runReport('Active Loans', { branch: '001' }).subscribe(r => (result = r));
    const req = http.expectOne(r => r.url.endsWith('/runreports/Active%20Loans') && r.params.get('branch') === '001');
    req.flush({ data: [{ n: 1 }], meta: {}, errors: [] }, { headers: { 'X-Report-Truncated': 'true' } });
    expect(result).toEqual({ rows: [{ n: 1 }], truncated: true });
    http.verify();
  });

  it('exportReport fetches the file as a blob with the format', () => {
    const http = TestBed.inject(HttpTestingController);
    service.exportReport('Active Loans', 'csv', { branch: '001' }).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/runreports/Active%20Loans/export')
      && r.params.get('format') === 'csv' && r.params.get('branch') === '001');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['a']));
    http.verify();
  });

  it('createReport POSTs the definition; enableReport POSTs to /enable', () => {
    service.createReport({ reportName: 'Mine', reportCategory: 'LOAN', reportSql: 'SELECT 1', reportParameters: [] }).subscribe();
    expect(api.post).toHaveBeenCalledWith('/reports', expect.objectContaining({ reportName: 'Mine' }));
    service.enableReport('r1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/reports/r1/enable', {});
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
