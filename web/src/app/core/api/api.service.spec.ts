import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { ApiResponse, PageResponse } from '../models/api-response.model';

/**
 * Tests the central HTTP wrapper every feature service depends on. The critical
 * behaviours are: (1) the standard CBA envelope is unwrapped to `.data`,
 * (2) query params are built correctly (including number→string coercion),
 * (3) the command/param helpers send the right body + query shape.
 */
describe('ApiService', () => {
  const BASE = 'http://localhost:8080/api/v1';
  let service: ApiService;
  let httpMock: HttpTestingController;

  /** Builds the standard `{ data, meta, errors }` envelope the backend returns. */
  function envelope<T>(data: T): ApiResponse<T> {
    return { data, meta: {}, errors: [] };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Fails the test if any unexpected request was made.
    httpMock.verify();
  });

  it('is created', () => {
    expect(service).toBeTruthy();
  });

  describe('get', () => {
    it('GETs the path and unwraps the envelope to .data', () => {
      let result: unknown;
      service.get<{ id: string }>('/customers/1').subscribe(r => (result = r));

      const req = httpMock.expectOne(`${BASE}/customers/1`);
      expect(req.request.method).toBe('GET');
      req.flush(envelope({ id: '1' }));

      expect(result).toEqual({ id: '1' });
    });

    it('serialises params and coerces numbers to strings', () => {
      service.get('/accounts', { type: 'SAVINGS', limit: 10 }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${BASE}/accounts`);
      expect(req.request.params.get('type')).toBe('SAVINGS');
      expect(req.request.params.get('limit')).toBe('10');
      req.flush(envelope([]));
    });
  });

  describe('getPage', () => {
    it('defaults page=0 & size=20 and returns the PageResponse', () => {
      let result: PageResponse<unknown> | undefined;
      service.getPage('/customers').subscribe(r => (result = r));

      const req = httpMock.expectOne(r => r.url === `${BASE}/customers`);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      const page: PageResponse<{ id: string }> = {
        content: [{ id: '1' }],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      };
      req.flush(envelope(page));

      expect(result?.content).toEqual([{ id: '1' }]);
      expect(result?.totalElements).toBe(1);
    });

    it('passes explicit paging + extra filter params', () => {
      service.getPage('/customers', 2, 50, { search: 'jane' }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${BASE}/customers`);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('search')).toBe('jane');
      req.flush(
        envelope({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 2 }),
      );
    });
  });

  describe('post / put / delete', () => {
    it('post sends the body and unwraps .data', () => {
      let result: unknown;
      service.post('/customers', { firstName: 'Jane' }).subscribe(r => (result = r));

      const req = httpMock.expectOne(`${BASE}/customers`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ firstName: 'Jane' });
      req.flush(envelope({ id: '9' }));

      expect(result).toEqual({ id: '9' });
    });

    it('put sends the body and unwraps .data', () => {
      service.put('/customers/1', { firstName: 'Jane' }).subscribe();

      const req = httpMock.expectOne(`${BASE}/customers/1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ firstName: 'Jane' });
      req.flush(envelope({ id: '1' }));
    });

    it('delete issues a DELETE and unwraps .data', () => {
      let result: unknown = 'unset';
      service.delete<null>('/customers/1').subscribe(r => (result = r));

      const req = httpMock.expectOne(`${BASE}/customers/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(envelope(null));

      expect(result).toBeNull();
    });
  });

  describe('postParams / putParams', () => {
    it('postParams sends an empty body with query params', () => {
      service.postParams('/tellers/1/activate', { note: 'go' }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${BASE}/tellers/1/activate`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      expect(req.request.params.get('note')).toBe('go');
      req.flush(envelope({}));
    });

    it('putParams sends an empty body with query params', () => {
      service.putParams('/config/x', { enabled: 'true' }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${BASE}/config/x`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({});
      expect(req.request.params.get('enabled')).toBe('true');
      req.flush(envelope({}));
    });
  });

  describe('command', () => {
    it('appends ?command= and posts the given body', () => {
      service.command('/loans/1', 'approve', { amount: 100 }).subscribe();

      // command() embeds the query in the URL string, so it stays in req.url
      // (not req.params, which only holds params passed via the options object).
      const req = httpMock.expectOne(`${BASE}/loans/1?command=approve`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ amount: 100 });
      req.flush(envelope({}));
    });

    it('defaults to an empty body when none is given', () => {
      service.command('/loans/1', 'disburse').subscribe();

      const req = httpMock.expectOne(`${BASE}/loans/1?command=disburse`);
      expect(req.request.body).toEqual({});
      req.flush(envelope({}));
    });
  });

  describe('postForm', () => {
    it('posts the FormData instance unchanged', () => {
      const form = new FormData();
      form.append('file', new Blob(['x']), 'a.png');
      service.postForm('/clients/1/images', form).subscribe();

      const req = httpMock.expectOne(`${BASE}/clients/1/images`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeInstanceOf(FormData);
      req.flush(envelope({ hasImage: true }));
    });
  });
});
