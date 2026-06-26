import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { CustomerDetailComponent } from './customer-detail';
import { CustomerService, Customer } from '../customer.service';
import { AccountService } from '../../accounts/account.service';
import { LoanService } from '../../loans/loan.service';

type CustSvc = Record<
  'get' | 'create' | 'executeCommand' | 'delete' | 'updateKycStatus' |
  'getIdentifiers' | 'getAddresses' | 'getBeneficiaries' |
  'getImageMeta' | 'getImageDataUrl' | 'uploadImage',
  ReturnType<typeof vi.fn>
>;
type AccSvc = Record<'list', ReturnType<typeof vi.fn>>;
type LoanSvc = Record<'list', ReturnType<typeof vi.fn>>;

function customer(over: Partial<Customer> = {}): Customer {
  return {
    id: 'cust-1', firstName: 'Jane', lastName: 'Doe', email: 'jane@x.com',
    kycStatus: 'ACTIVE', createdAt: '2026-01-01', ...over,
  };
}
function page<T>(content: T[]) {
  return of({ content, totalElements: content.length, totalPages: 1, size: 50, number: 0 });
}

describe('CustomerDetailComponent', () => {
  let custSvc: CustSvc;
  let accSvc: AccSvc;
  let loanSvc: LoanSvc;

  beforeEach(() => {
    custSvc = {
      get: vi.fn().mockReturnValue(of(customer())),
      create: vi.fn().mockReturnValue(of(customer({ id: 'cust-new' }))),
      executeCommand: vi.fn().mockReturnValue(of(customer({ kycStatus: 'REJECTED' }))),
      delete: vi.fn().mockReturnValue(of(undefined)),
      updateKycStatus: vi.fn().mockReturnValue(of(customer({ kycStatus: 'SUSPENDED' }))),
      getIdentifiers: vi.fn().mockReturnValue(of([])),
      getAddresses: vi.fn().mockReturnValue(of([])),
      getBeneficiaries: vi.fn().mockReturnValue(of([])),
      getImageMeta: vi.fn().mockReturnValue(of({ hasImage: false })),
      getImageDataUrl: vi.fn().mockReturnValue(of('blob:url')),
      uploadImage: vi.fn().mockReturnValue(of({ hasImage: true })),
    };
    accSvc = { list: vi.fn().mockReturnValue(page([{ id: 'a1', status: 'ACTIVE' }])) };
    loanSvc = { list: vi.fn().mockReturnValue(page([{ id: 'l1', status: 'ACTIVE' }])) };
    configure('cust-1');
  });

  function configure(routeId: string) {
    TestBed.configureTestingModule({
      imports: [CustomerDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CustomerService, useValue: custSvc },
        { provide: AccountService, useValue: accSvc },
        { provide: LoanService, useValue: loanSvc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    });
  }

  function make() {
    const fixture = TestBed.createComponent(CustomerDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the customer + image meta from the route id on init and renders', () => {
    const c = make();
    expect(custSvc.get).toHaveBeenCalledWith('cust-1');
    expect(custSvc.getImageMeta).toHaveBeenCalledWith('cust-1');
    expect(c.customer?.id).toBe('cust-1');
    expect(c.loading).toBe(false);
    expect(c.activeTab).toBe('overview');
  });

  it('fetches the image data url when the customer has an image', () => {
    custSvc.getImageMeta.mockReturnValue(of({ hasImage: true }));
    const c = make();
    expect(custSvc.getImageDataUrl).toHaveBeenCalledWith('cust-1');
    expect(c.photoDataUrl).toBe('blob:url');
  });

  it('sets an error when the customer is not found', () => {
    custSvc.get.mockReturnValue(throwError(() => new Error('404')));
    const c = make();
    expect(c.customer).toBeNull();
    expect(c.error).toBe('Customer not found.');
    expect(c.loading).toBe(false);
  });

  it('enters creation mode for the "new" route without loading a customer', () => {
    TestBed.resetTestingModule();
    configure('new');
    const c = make();
    expect(custSvc.get).not.toHaveBeenCalled();
    expect(c.isNew).toBe(true);
    expect(c.loading).toBe(false);
  });

  describe('tab navigation lazy-loads tab data once', () => {
    it('accounts tab loads the customer accounts', () => {
      const c = make();
      c.selectTab('accounts');
      expect(accSvc.list).toHaveBeenCalledWith(0, 50, 'cust-1');
      expect(c.accounts).toHaveLength(1);
      expect(c.accountsLoaded).toBe(true);
      c.selectTab('overview');
      c.selectTab('accounts');
      expect(accSvc.list).toHaveBeenCalledTimes(1);
    });

    it('loans tab loads the customer loans', () => {
      const c = make();
      c.selectTab('loans');
      expect(loanSvc.list).toHaveBeenCalledWith(0, 50, undefined, 'cust-1');
      expect(c.loans).toHaveLength(1);
      expect(c.loansLoaded).toBe(true);
    });

    it('identifiers tab loads identifiers and addresses', () => {
      const c = make();
      c.selectTab('identifiers');
      expect(custSvc.getIdentifiers).toHaveBeenCalledWith('cust-1');
      expect(custSvc.getAddresses).toHaveBeenCalledWith('cust-1');
      expect(c.identifiersLoaded).toBe(true);
    });
  });

  describe('command modals (representative)', () => {
    it('rejectCustomer runs the reject command with the reason', () => {
      const c = make();
      c.rejectReason = 'bad docs';
      c.rejectCustomer();
      expect(custSvc.executeCommand).toHaveBeenCalledWith('cust-1', 'reject', { reason: 'bad docs' });
      expect(c.customer?.kycStatus).toBe('REJECTED');
      expect(c.showRejectModal).toBe(false);
      expect(c.cmdWorking).toBe(false);
    });

    it('assignStaff requires a staff id and trims it', () => {
      const c = make();
      c.assignStaffId = '   ';
      c.assignStaff();
      expect(custSvc.executeCommand).not.toHaveBeenCalled();
      c.assignStaffId = '  staff-7  ';
      c.assignStaff();
      expect(custSvc.executeCommand).toHaveBeenCalledWith('cust-1', 'assignStaff', { staffId: 'staff-7' });
    });

    it('proposeTransfer requires a destination office', () => {
      const c = make();
      c.transferDestinationOfficeId = '';
      c.proposeTransfer();
      expect(custSvc.executeCommand).not.toHaveBeenCalled();
      c.transferDestinationOfficeId = 'office-2';
      c.transferDate = '2026-07-01';
      c.proposeTransfer();
      expect(custSvc.executeCommand).toHaveBeenCalledWith('cust-1', 'proposeTransfer', expect.objectContaining({
        destinationOfficeId: 'office-2', transferDate: '2026-07-01',
      }));
    });

    it('a failed command surfaces the server error message', () => {
      custSvc.executeCommand.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'not allowed' }] } })));
      const c = make();
      c.reactivateCustomer();
      expect(c.cmdError).toBe('not allowed');
      expect(c.cmdWorking).toBe(false);
    });
  });

  describe('KYC dropdown (legacy)', () => {
    it('confirmKycChange updates the status', () => {
      const c = make();
      c.pendingKycStatus = 'SUSPENDED';
      c.confirmKycChange();
      expect(custSvc.updateKycStatus).toHaveBeenCalledWith('cust-1', 'SUSPENDED');
      expect(c.customer?.kycStatus).toBe('SUSPENDED');
      expect(c.showKycDropdown).toBe(false);
    });
  });

  describe('delete', () => {
    it('confirmDelete navigates back on success', () => {
      const c = make();
      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      c.confirmDelete();
      expect(custSvc.delete).toHaveBeenCalledWith('cust-1');
      expect(navSpy).toHaveBeenCalled();
    });
  });

  describe('create', () => {
    it('submitCreate requires the mandatory fields', () => {
      TestBed.resetTestingModule();
      configure('new');
      const c = make();
      c.newForm = { firstName: '', lastName: 'Doe', email: 'x@y.com' };
      c.submitCreate();
      expect(custSvc.create).not.toHaveBeenCalled();
    });

    it('submitCreate creates and navigates to the new customer', () => {
      TestBed.resetTestingModule();
      configure('new');
      const fixture = TestBed.createComponent(CustomerDetailComponent);
      fixture.detectChanges();
      const c = fixture.componentInstance;
      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      c.newForm = { firstName: 'New', lastName: 'Guy', email: 'new@x.com' };
      c.submitCreate();
      expect(custSvc.create).toHaveBeenCalledWith(c.newForm);
      expect(navSpy).toHaveBeenCalled();
    });
  });

  describe('helpers', () => {
    let c: CustomerDetailComponent;
    beforeEach(() => { c = make(); });

    it('initials uppercases the first letters', () => {
      c.customer = customer({ firstName: 'jane', lastName: 'doe' });
      expect(c.initials).toBe('JD');
    });

    it('availableKycTransitions reflects the current status', () => {
      c.customer = customer({ kycStatus: 'ACTIVE' });
      expect(c.availableKycTransitions).toEqual(['SUSPENDED', 'CLOSED']);
      c.customer = customer({ kycStatus: 'CLOSED' });
      expect(c.availableKycTransitions).toEqual([]);
    });

    it('kycVariant / kycLabel map statuses', () => {
      expect(c.kycVariant('ACTIVE')).toBe('success');
      expect(c.kycVariant('PENDING_KYC')).toBe('warning');
      expect(c.kycVariant('REJECTED')).toBe('error');
      expect(c.kycVariant('TRANSFER_IN_PROGRESS')).toBe('info');
      expect(c.kycLabel('PENDING_KYC')).toBe('Pending KYC');
    });

    it('accountStatusVariant / loanStatusVariant map statuses', () => {
      expect(c.accountStatusVariant('ACTIVE')).toBe('success');
      expect(c.accountStatusVariant('DORMANT')).toBe('warning');
      expect(c.accountStatusVariant('FROZEN')).toBe('error');
      expect(c.loanStatusVariant('ACTIVE')).toBe('primary');
      expect(c.loanStatusVariant('IN_ARREARS')).toBe('error');
      expect(c.loanStatusLabel('UNDER_REVIEW')).toBe('Under Review');
    });

    it('closeAllModals clears every modal flag and form field', () => {
      c.showRejectModal = true;
      c.rejectReason = 'x';
      c.assignStaffId = 'y';
      c.cmdError = 'z';
      c.closeAllModals();
      expect(c.showRejectModal).toBe(false);
      expect(c.rejectReason).toBe('');
      expect(c.assignStaffId).toBe('');
      expect(c.cmdError).toBe('');
    });
  });
});
