import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FraudRulesComponent } from './fraud-rules';
import { CardsService, FraudRule } from '../cards.service';

type Svc = Record<'listFraudRules' | 'updateFraudRule', ReturnType<typeof vi.fn>>;

function rule(over: Partial<FraudRule> = {}): FraudRule {
  return { id: 'fr1', ruleId: 'VELOCITY_LIMIT', weight: 40, enabled: true, params: { velocityCount: 5 }, ...over };
}

describe('FraudRulesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFraudRules: vi.fn().mockReturnValue(of([rule()])),
      updateFraudRule: vi.fn().mockReturnValue(of(rule({ weight: 55 }))),
    };
    TestBed.configureTestingModule({
      imports: [FraudRulesComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FraudRulesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads rules on init', () => {
    const c = make();
    expect(svc.listFraudRules).toHaveBeenCalled();
    expect(c.rules).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false on error', () => {
    svc.listFraudRules.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  describe('editing', () => {
    it('startEdit seeds the form and serialises params JSON', () => {
      const c = make();
      c.startEdit(rule({ id: 'fr1', weight: 40, params: { a: 1 } }));
      expect(c.editingId).toBe('fr1');
      expect(c.editForm.weight).toBe(40);
      expect(JSON.parse(c.paramsJson)).toEqual({ a: 1 });
    });

    it('cancelEdit clears the editing id', () => {
      const c = make();
      c.startEdit(rule());
      c.cancelEdit();
      expect(c.editingId).toBeNull();
    });

    it('saveEdit parses JSON, updates and reloads', () => {
      const c = make();
      c.startEdit(rule({ id: 'fr1' }));
      c.editForm.weight = 55;
      c.paramsJson = '{"velocityCount":7}';
      svc.listFraudRules.mockClear();
      c.saveEdit('fr1');
      expect(svc.updateFraudRule).toHaveBeenCalledWith('fr1', expect.objectContaining({ weight: 55, params: { velocityCount: 7 } }));
      expect(c.editingId).toBeNull();
      expect(svc.listFraudRules).toHaveBeenCalled();
    });

    it('saveEdit aborts on invalid JSON without calling the service', () => {
      const c = make();
      c.startEdit(rule({ id: 'fr1' }));
      c.paramsJson = '{ not json';
      c.saveEdit('fr1');
      expect(svc.updateFraudRule).not.toHaveBeenCalled();
      expect(c.editingId).toBe('fr1');
    });
  });

  it('isHardBlock flags the three terminal rules', () => {
    const c = make();
    expect(c.isHardBlock('CARD_EXPIRED')).toBe(true);
    expect(c.isHardBlock('CARD_BLOCKED')).toBe(true);
    expect(c.isHardBlock('PIN_RETRY_EXCEEDED')).toBe(true);
    expect(c.isHardBlock('VELOCITY_LIMIT')).toBe(false);
  });

  it('scoreColor bands weight into low/medium/high', () => {
    const c = make();
    expect(c.scoreColor(10)).toBe('low');
    expect(c.scoreColor(29)).toBe('low');
    expect(c.scoreColor(30)).toBe('medium');
    expect(c.scoreColor(69)).toBe('medium');
    expect(c.scoreColor(70)).toBe('high');
    expect(c.scoreColor(100)).toBe('high');
  });
});
