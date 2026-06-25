import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FraudRulesAdminComponent } from './fraud-rules-admin';
import { AdminService, FraudRule } from './admin.service';

type Svc = Record<'listFraudRules' | 'updateFraudRule', ReturnType<typeof vi.fn>>;

function rule(over: Partial<FraudRule> = {}): FraudRule {
  return {
    id: 'fr1', name: 'Velocity', ruleType: 'VELOCITY', enabled: true,
    blocking: false, severity: 'HIGH', params: '{"n":5}', ...over,
  };
}
function page(content: FraudRule[]) {
  return of({ content, totalElements: content.length, totalPages: 1, size: 50, number: 0 });
}

describe('FraudRulesAdminComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFraudRules: vi.fn().mockReturnValue(page([rule()])),
      updateFraudRule: vi.fn().mockReturnValue(of(rule({ enabled: false }))),
    };
    TestBed.configureTestingModule({
      imports: [FraudRulesAdminComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FraudRulesAdminComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads rules on init', () => {
    const c = make();
    expect(svc.listFraudRules).toHaveBeenCalledWith(0, 50);
    expect(c.rules).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on load failure', () => {
    svc.listFraudRules.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('toggleEnabled flips the enabled flag via the service', () => {
    const c = make();
    const r = rule({ enabled: true });
    c.toggleEnabled(r);
    expect(svc.updateFraudRule).toHaveBeenCalledWith('fr1', { enabled: false });
    expect(r.enabled).toBe(false);
    expect(c.saving).toBeNull();
  });

  it('toggleEnabled clears the saving marker on error', () => {
    svc.updateFraudRule.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    c.toggleEnabled(rule());
    expect(c.saving).toBeNull();
  });

  it('openEdit primes the edit form', () => {
    const c = make();
    c.openEdit(rule({ params: '{"a":1}', severity: 'LOW', blocking: true }));
    expect(c.editRule).not.toBeNull();
    expect(c.editParams).toBe('{"a":1}');
    expect(c.editSeverity).toBe('LOW');
    expect(c.editBlocking).toBe(true);
    expect(c.showEditModal).toBe(true);
  });

  it('openEdit defaults params to {} when null', () => {
    const c = make();
    c.openEdit(rule({ params: undefined as never }));
    expect(c.editParams).toBe('{}');
  });

  it('confirmEdit persists the edited fields and replaces the rule', () => {
    svc.updateFraudRule.mockReturnValue(of(rule({ severity: 'CRITICAL' })));
    const c = make();
    c.openEdit(rule());
    c.editSeverity = 'CRITICAL';
    c.editBlocking = true;
    c.editParams = '{"x":1}';
    c.confirmEdit();
    expect(svc.updateFraudRule).toHaveBeenCalledWith('fr1', { severity: 'CRITICAL', blocking: true, params: '{"x":1}' });
    expect(c.rules.find(r => r.id === 'fr1')!.severity).toBe('CRITICAL');
    expect(c.showEditModal).toBe(false);
  });

  it('confirmEdit is a no-op without an editRule', () => {
    const c = make();
    c.editRule = null;
    c.confirmEdit();
    expect(svc.updateFraudRule).not.toHaveBeenCalled();
  });

  it('severityChip maps severities', () => {
    const c = make();
    expect(c.severityChip('LOW')).toBe('success');
    expect(c.severityChip('MEDIUM')).toBe('warning');
    expect(c.severityChip('HIGH')).toBe('error');
    expect(c.severityChip('CRITICAL')).toBe('critical');
    expect(c.severityChip('UNKNOWN')).toBe('neutral');
  });

  it('formatParams pretty-prints valid JSON and echoes invalid', () => {
    const c = make();
    expect(c.formatParams('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(c.formatParams('nope')).toBe('nope');
  });
});
