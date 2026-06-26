import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { LoginComponent } from './login';
import { environment } from '../../../environments/environment';

describe('LoginComponent', () => {
  const originalBypass = environment.authBypass;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let keycloak: { login: ReturnType<typeof vi.fn> };

  function setup() {
    router = { navigate: vi.fn() };
    keycloak = { login: vi.fn() };
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: Keycloak, useValue: keycloak },
      ],
    });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges(); // full-template render
    return fixture.componentInstance;
  }

  afterEach(() => {
    environment.authBypass = originalBypass;
  });

  it('renders without error and exposes the current year', () => {
    environment.authBypass = true;
    const c = setup();
    expect(c.currentYear).toBe(new Date().getFullYear());
  });

  it('in bypass mode, signIn navigates to the dashboard (no Keycloak)', () => {
    environment.authBypass = true;
    const c = setup();
    c.signIn();
    expect(router.navigate).toHaveBeenCalledWith(['/operations/dashboard']);
    expect(keycloak.login).not.toHaveBeenCalled();
  });

  it('in real mode, signIn delegates to keycloak.login (no navigation)', () => {
    environment.authBypass = false;
    const c = setup();
    c.signIn();
    expect(keycloak.login).toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
