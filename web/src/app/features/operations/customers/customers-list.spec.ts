import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CustomersListComponent } from './customers-list';
import { CustomerService } from './customer.service';

describe('CustomersListComponent', () => {
  let svc: { list: ReturnType<typeof vi.fn> };

  function page<T>(content: T[], totalElements: number) {
    return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
  }

  beforeEach(() => {
    svc = { list: vi.fn().mockReturnValue(page([{ id: 'c1', firstName: 'Jane', lastName: 'Doe', kycStatus: 'ACTIVE' }], 1)) };
    TestBed.configureTestingModule({
      imports: [CustomersListComponent],
      providers: [provideRouter([]), { provide: CustomerService, useValue: svc }],
    });
  });

  it('loads the first page on init', () => {
    const fixture = TestBed.createComponent(CustomersListComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(svc.list).toHaveBeenCalledWith(0, 20, undefined, undefined);
    expect(c.customers).toHaveLength(1);
    expect(c.totalElements).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('onFilter resets to page 0 and reloads with the filter', () => {
    const fixture = TestBed.createComponent(CustomersListComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    c.page = 3;
    c.onFilter('PENDING_KYC');
    expect(c.activeFilter).toBe('PENDING_KYC');
    expect(c.page).toBe(0);
    expect(svc.list).toHaveBeenLastCalledWith(0, 20, undefined, 'PENDING_KYC');
  });

  describe('pagination bounds', () => {
    let c: CustomersListComponent;
    beforeEach(() => {
      // loadPage() (called by next/prevPage) re-reads totalElements from the service,
      // so the mock must echo the same total to keep the bounds stable.
      svc.list.mockReturnValue(of({ content: [], totalElements: 50, totalPages: 3, size: 20, number: 0 }));
      c = TestBed.createComponent(CustomersListComponent).componentInstance;
      c.totalElements = 50; // 3 pages at size 20
    });

    it('nextPage advances but never past the last page', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2); // (2+1)*20=60 ≥ 50 → no advance
    });

    it('prevPage decrements but never below 0', () => {
      c.page = 1; c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });

    it('row-count getters compute the visible window', () => {
      c.page = 0;
      expect(c.totalPages).toBe(3);
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(20);
      c.page = 2;
      expect(c.startRow).toBe(41);
      expect(c.endRow).toBe(50); // capped at totalElements
    });

    it('startRow is 0 when there are no rows', () => {
      c.totalElements = 0;
      expect(c.startRow).toBe(0);
    });
  });

  describe('presentation helpers', () => {
    let c: CustomersListComponent;
    beforeEach(() => {
      c = TestBed.createComponent(CustomersListComponent).componentInstance;
    });

    it('initials uppercases the first letters', () => {
      expect(c.initials({ firstName: 'jane', lastName: 'doe' } as never)).toBe('JD');
    });

    it('kycVariant maps statuses to badge variants', () => {
      expect(c.kycVariant('ACTIVE')).toBe('success');
      expect(c.kycVariant('PENDING_KYC')).toBe('warning');
      expect(c.kycVariant('SUSPENDED')).toBe('error');
      expect(c.kycVariant('CLOSED')).toBe('neutral');
    });

    it('kycLabel humanises the status', () => {
      expect(c.kycLabel('PENDING_KYC')).toBe('Pending KYC');
      expect(c.kycLabel('ACTIVE')).toBe('Active');
    });

    it('avatarColor wraps the palette', () => {
      expect(c.avatarColor(0)).toBe(c.avatarColor(7)); // 7 % 7 === 0
    });
  });
});
