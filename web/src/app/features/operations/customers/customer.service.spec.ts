import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { CustomerService } from './customer.service';

const BASE = 'http://localhost:8080/api/v1';

describe('CustomerService', () => {
  let service: CustomerService;
  let api: Record<'get' | 'getPage' | 'post' | 'put' | 'delete', ReturnType<typeof vi.fn>>;
  let http: Record<'get' | 'put' | 'delete', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
    };
    http = {
      get: vi.fn(),
      put: vi.fn().mockReturnValue(of({ data: {} })),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [
        CustomerService,
        { provide: ApiService, useValue: api },
        { provide: HttpClient, useValue: http },
      ],
    });
    service = TestBed.inject(CustomerService);
  });

  // ── ApiService-delegating methods ──────────────────────────────────────────

  it('list() pages /customers with search + kycStatus filters', () => {
    service.list(1, 10, 'jane', 'ACTIVE').subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/customers', 1, 10, {
      search: 'jane',
      kycStatus: 'ACTIVE',
    });
  });

  it('list() omits empty filters', () => {
    service.list().subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/customers', 0, 20, {});
  });

  it('get / create / update / delete route correctly', () => {
    service.get('c1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/customers/c1');

    const create = { firstName: 'J', lastName: 'D', email: 'j@d.com' };
    service.create(create).subscribe();
    expect(api.post).toHaveBeenCalledWith('/customers', create);

    service.update('c1', { phone: '123' }).subscribe();
    expect(api.put).toHaveBeenCalledWith('/customers/c1', { phone: '123' });

    service.delete('c1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/customers/c1');
  });

  it('executeCommand() uses the Mifos ?command= pattern with the payload', () => {
    service.executeCommand('c1', 'reject', { reason: 'fraud' }).subscribe();
    expect(api.post).toHaveBeenCalledWith('/customers/c1?command=reject', { reason: 'fraud' });
  });

  it('executeCommand() defaults to an empty payload', () => {
    service.executeCommand('c1', 'activate').subscribe();
    expect(api.post).toHaveBeenCalledWith('/customers/c1?command=activate', {});
  });

  it('updateKycStatus() PUTs the legacy kyc-status endpoint', () => {
    service.updateKycStatus('c1', 'SUSPENDED').subscribe();
    expect(api.put).toHaveBeenCalledWith('/customers/c1/kyc-status', { kycStatus: 'SUSPENDED' });
  });

  it('client sub-resources (identifiers/addresses/beneficiaries) route correctly', () => {
    service.getIdentifiers('c1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/clients/c1/identifiers');
    service.getAddresses('c1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/clients/c1/addresses');
    service.getBeneficiaries('c1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/clients/c1/beneficiaries');
  });

  // ── Image methods (direct HttpClient + token + blob) ───────────────────────

  it('getImageMeta() unwraps .data and sends a Bearer token', () => {
    http.get.mockReturnValue(of({ data: { hasImage: true } }));
    let meta: unknown;
    service.getImageMeta('c1').subscribe(m => (meta = m));

    expect(meta).toEqual({ hasImage: true });
    const [url, opts] = http.get.mock.calls[0];
    expect(url).toBe(`${BASE}/clients/c1/images`);
    expect(opts.headers.get('Authorization')).toMatch(/^Bearer /);
  });

  it('getImageMeta() falls back to the dev-bypass token when none is stored', () => {
    localStorage.removeItem('access_token');
    http.get.mockReturnValue(of({ data: {} }));
    service.getImageMeta('c1').subscribe();

    const opts = http.get.mock.calls[0][1];
    expect(opts.headers.get('Authorization')).toBe('Bearer dev-bypass-token');
  });

  it('getImageDataUrl() converts the blob to an object URL', () => {
    const original = URL.createObjectURL;
    URL.createObjectURL = vi.fn(() => 'blob:fake-url');
    http.get.mockReturnValue(of(new Blob(['x'])));

    let url: string | undefined;
    service.getImageDataUrl('c1').subscribe(u => (url = u));

    expect(url).toBe('blob:fake-url');
    expect(http.get.mock.calls[0][1].responseType).toBe('blob');
    URL.createObjectURL = original;
  });

  it('uploadImage() PUTs multipart FormData', () => {
    const file = new File(['x'], 'photo.png', { type: 'image/png' });
    service.uploadImage('c1', file).subscribe();

    const [url, body] = http.put.mock.calls[0];
    expect(url).toBe(`${BASE}/clients/c1/images`);
    expect(body).toBeInstanceOf(FormData);
  });
});
