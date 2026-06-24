import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { authInterceptor } from './auth.interceptor';
import { environment } from '../../../environments/environment';

/**
 * The interceptor has four behaviours worth pinning:
 *  - bypass mode  → never touch the request (no token available)
 *  - real mode + token + /api/ URL → attach Bearer + X-Tenant-ID
 *  - real mode + token + non-/api URL → leave it alone (don't leak the token)
 *  - real mode + no token → pass through untouched
 */
describe('authInterceptor', () => {
  const originalBypass = environment.authBypass;
  let http: HttpClient;
  let httpMock: HttpTestingController;

  function setup(token: string | undefined) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: { token } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    environment.authBypass = originalBypass;
    httpMock?.verify();
  });

  it('in bypass mode, sends no auth headers', () => {
    environment.authBypass = true;
    setup('tok-should-be-ignored');

    http.get('http://localhost:8080/api/v1/customers').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/customers');

    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.has('X-Tenant-ID')).toBe(false);
    req.flush({});
  });

  it('attaches Bearer token + X-Tenant-ID for /api/ calls in real mode', () => {
    environment.authBypass = false;
    setup('tok123');

    http.get('http://localhost:8080/api/v1/customers').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/customers');

    expect(req.request.headers.get('Authorization')).toBe('Bearer tok123');
    expect(req.request.headers.get('X-Tenant-ID')).toBe('default');
    req.flush({});
  });

  it('does NOT attach the token to non-/api/ URLs', () => {
    environment.authBypass = false;
    setup('tok123');

    http.get('http://localhost:8080/assets/config.json').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/assets/config.json');

    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('passes the request through untouched when there is no token', () => {
    environment.authBypass = false;
    setup(undefined);

    http.get('http://localhost:8080/api/v1/customers').subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/v1/customers');

    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
