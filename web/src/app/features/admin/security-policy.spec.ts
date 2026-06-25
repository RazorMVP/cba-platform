import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SecurityPolicyComponent } from './security-policy';
import { AdminService, SecurityPolicy } from './admin.service';

type Svc = Record<'getSecurityPolicy' | 'updateSecurityPolicy', ReturnType<typeof vi.fn>>;

function policy(over: Partial<SecurityPolicy> = {}): SecurityPolicy {
  return {
    bruteForceProtected: true, maxLoginFailures: 5, lockoutDurationSeconds: 900,
    failureResetWindowSeconds: 600, minPasswordLength: 8, requireUppercase: true,
    requireLowercase: true, requireDigits: true, requireSpecialChars: false,
    passwordHistoryCount: 3, ssoSessionIdleTimeoutSeconds: 1800,
    ssoSessionMaxLifespanSeconds: 36000, accessTokenLifespanSeconds: 300,
    rawPasswordPolicy: 'length(8)', ...over,
  };
}

describe('SecurityPolicyComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getSecurityPolicy: vi.fn().mockReturnValue(of(policy())),
      updateSecurityPolicy: vi.fn().mockReturnValue(of(policy({ minPasswordLength: 12 }))),
    };
    TestBed.configureTestingModule({
      imports: [SecurityPolicyComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SecurityPolicyComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the policy on init', () => {
    const c = make();
    expect(svc.getSecurityPolicy).toHaveBeenCalled();
    expect(c.policy()).not.toBeNull();
    expect(c.loading()).toBe(false);
  });

  it('shows a Keycloak-unavailable error on load failure', () => {
    svc.getSecurityPolicy.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error()).toContain('Keycloak may be unavailable');
    expect(c.loading()).toBe(false);
  });

  it('enterEdit copies the policy into the form', () => {
    const c = make();
    c.enterEdit();
    expect(c.editMode()).toBe(true);
    expect(c.form().minPasswordLength).toBe(8);
    expect(c.saved()).toBe(false);
  });

  it('enterEdit is a no-op when policy is null', () => {
    svc.getSecurityPolicy.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    c.enterEdit();
    expect(c.editMode()).toBe(false);
  });

  it('cancel exits edit mode', () => {
    const c = make();
    c.enterEdit();
    c.cancel();
    expect(c.editMode()).toBe(false);
  });

  describe('save', () => {
    it('persists the form and marks saved', () => {
      const c = make();
      c.enterEdit();
      c.setForm('minPasswordLength', 12);
      c.save();
      expect(svc.updateSecurityPolicy).toHaveBeenCalledWith(expect.objectContaining({ minPasswordLength: 12 }));
      expect(c.editMode()).toBe(false);
      expect(c.saved()).toBe(true);
      expect(c.policy()!.minPasswordLength).toBe(12);
    });
    it('surfaces the server error on failure', () => {
      svc.updateSecurityPolicy.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'nope' }] } })));
      const c = make();
      c.enterEdit();
      c.save();
      expect(c.error()).toBe('nope');
      expect(c.saving()).toBe(false);
    });
  });

  it('setForm updates a single key immutably', () => {
    const c = make();
    c.enterEdit();
    c.setForm('requireSpecialChars', true);
    expect(c.form().requireSpecialChars).toBe(true);
  });

  it('seconds/minutes converters round-trip', () => {
    const c = make();
    expect(c.secondsToMinutes(900)).toBe(15);
    expect(c.minutesToSeconds(15)).toBe(900);
  });
});
