import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BulkImportComponent } from './bulk-import';
import { AdminService, BulkImportResult } from './admin.service';

type Svc = Record<'importCustomers' | 'importLoans' | 'bulkImportJobs', ReturnType<typeof vi.fn>>;

const result: BulkImportResult = {
  jobId: 'j1', entityType: 'CUSTOMERS', totalRows: 3, successCount: 2, failureCount: 1,
  status: 'PARTIAL', errors: [{ row: 3, field: 'email', message: 'bad' }],
};

function csvFile() {
  return new File(['a,b'], 'data.csv', { type: 'text/csv' });
}

describe('BulkImportComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      importCustomers: vi.fn().mockReturnValue(of(result)),
      importLoans: vi.fn().mockReturnValue(of({ ...result, entityType: 'LOANS' })),
      bulkImportJobs: vi.fn().mockReturnValue(of([{ id: 'job1', status: 'COMPLETED' }])),
    };
    TestBed.configureTestingModule({
      imports: [BulkImportComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(BulkImportComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('renders without auto-loading (no OnInit)', () => {
    const c = make();
    expect(svc.importCustomers).not.toHaveBeenCalled();
    expect(c.entityType).toBe('CUSTOMERS');
  });

  it('onFileSelected captures the chosen file and clears prior state', () => {
    const c = make();
    c.result.set(result);
    const file = csvFile();
    const event = { target: { files: [file] } } as unknown as Event;
    c.onFileSelected(event);
    expect(c.selectedFile).toBe(file);
    expect(c.result()).toBeNull();
    expect(c.error()).toBeNull();
  });

  it('onDrop captures the dropped file', () => {
    const c = make();
    const file = csvFile();
    const event = { preventDefault: vi.fn(), dataTransfer: { files: [file] } } as unknown as DragEvent;
    c.onDrop(event);
    expect(c.selectedFile).toBe(file);
  });

  describe('upload', () => {
    it('is a no-op without a selected file', () => {
      const c = make();
      c.upload();
      expect(svc.importCustomers).not.toHaveBeenCalled();
    });
    it('imports customers for the CUSTOMERS entity type', () => {
      const c = make();
      c.selectedFile = csvFile();
      c.entityType = 'CUSTOMERS';
      c.upload();
      expect(svc.importCustomers).toHaveBeenCalledWith(c.selectedFile);
      expect(c.result()).toEqual(result);
      expect(c.uploading()).toBe(false);
      expect(svc.bulkImportJobs).toHaveBeenCalled();
    });
    it('imports loans for the LOANS entity type', () => {
      const c = make();
      c.selectedFile = csvFile();
      c.entityType = 'LOANS';
      c.upload();
      expect(svc.importLoans).toHaveBeenCalled();
    });
    it('extracts the server error message on failure', () => {
      svc.importCustomers.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'too big' }] } })));
      const c = make();
      c.selectedFile = csvFile();
      c.upload();
      expect(c.error()).toBe('too big');
      expect(c.uploading()).toBe(false);
    });
    it('falls back to a generic message when none provided', () => {
      svc.importCustomers.mockReturnValue(throwError(() => ({})));
      const c = make();
      c.selectedFile = csvFile();
      c.upload();
      expect(c.error()).toBe('Upload failed');
    });
  });

  it('toggleHistory reveals and loads jobs once', () => {
    const c = make();
    c.toggleHistory();
    expect(c.showHistory()).toBe(true);
    expect(svc.bulkImportJobs).toHaveBeenCalledWith('CUSTOMERS');
    expect(c.jobs()).toHaveLength(1);
    c.toggleHistory();
    expect(c.showHistory()).toBe(false);
  });

  it('statusClass maps statuses', () => {
    const c = make();
    expect(c.statusClass('COMPLETED')).toBe('success');
    expect(c.statusClass('PARTIAL')).toBe('warning');
    expect(c.statusClass('FAILED')).toBe('error');
  });

  it('exposes CSV templates for both entity types', () => {
    const c = make();
    expect(c.templates.CUSTOMERS).toContain('firstName');
    expect(c.templates.LOANS).toContain('principalAmount');
  });
});
