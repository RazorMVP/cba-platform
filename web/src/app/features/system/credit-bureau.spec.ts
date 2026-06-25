import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CreditBureauComponent } from './credit-bureau';
import { SystemService, CreditBureau, CreditBureauMapping } from './system.service';

type Svc = Record<
  'listCreditBureaus' | 'createCreditBureau' | 'updateCreditBureau' | 'deleteCreditBureau' |
  'activateCreditBureau' | 'deactivateCreditBureau' | 'listCreditBureauMappings' |
  'createCreditBureauMapping' | 'deleteCreditBureauMapping',
  ReturnType<typeof vi.fn>
>;

function bureau(over: Partial<CreditBureau> = {}): CreditBureau {
  return { id: 'cb1', name: 'TransUnion', country: 'KE', implClass: 'com.x.TU', active: true, description: 'desc', ...over };
}
function mapping(over: Partial<CreditBureauMapping> = {}): CreditBureauMapping {
  return { id: 'm1', creditBureauId: 'cb1', loanProductId: 'lp1', loanProductName: 'Personal', creditCheckMandatory: true, ...over };
}

describe('CreditBureauComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listCreditBureaus: vi.fn().mockReturnValue(of([bureau()])),
      createCreditBureau: vi.fn().mockReturnValue(of(bureau({ id: 'cb2' }))),
      updateCreditBureau: vi.fn().mockReturnValue(of(bureau({ id: 'cb1' }))),
      deleteCreditBureau: vi.fn().mockReturnValue(of(void 0)),
      activateCreditBureau: vi.fn().mockReturnValue(of(bureau({ active: true }))),
      deactivateCreditBureau: vi.fn().mockReturnValue(of(bureau({ active: false }))),
      listCreditBureauMappings: vi.fn().mockReturnValue(of([mapping()])),
      createCreditBureauMapping: vi.fn().mockReturnValue(of(mapping({ id: 'm2' }))),
      deleteCreditBureauMapping: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [CreditBureauComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CreditBureauComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads bureaus on init', () => {
    const c = make();
    expect(svc.listCreditBureaus).toHaveBeenCalled();
    expect(c.bureaus).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listCreditBureaus.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load credit bureaus.');
  });

  describe('toggleExpand + lazy mappings', () => {
    it('lazy-loads mappings on first expand', () => {
      const c = make();
      c.toggleExpand('cb1');
      expect(c.expandedBureau).toBe('cb1');
      expect(svc.listCreditBureauMappings).toHaveBeenCalledWith('cb1');
      expect(c.mappings['cb1']).toHaveLength(1);
      expect(c.mappingsLoading['cb1']).toBe(false);
    });

    it('collapses without reloading on a second expand', () => {
      const c = make();
      c.toggleExpand('cb1');
      c.toggleExpand('cb1');
      expect(c.expandedBureau).toBeNull();
      expect(svc.listCreditBureauMappings).toHaveBeenCalledTimes(1);
    });

    it('does not reload mappings already cached', () => {
      const c = make();
      c.mappings['cb1'] = [mapping()];
      c.toggleExpand('cb1');
      expect(svc.listCreditBureauMappings).not.toHaveBeenCalled();
    });
  });

  describe('save', () => {
    it('creates when no edit target then reloads', () => {
      const c = make();
      c.openCreate();
      c.form = { name: 'Metropol', country: 'KE', implClass: 'com.x.M', description: '' };
      c.save();
      expect(svc.createCreditBureau).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listCreditBureaus).toHaveBeenCalledTimes(2);
    });

    it('updates when an edit target is set', () => {
      const c = make();
      c.openEdit(bureau({ id: 'cb1' }));
      c.save();
      expect(svc.updateCreditBureau).toHaveBeenCalledWith('cb1', c.form);
    });

    it('surfaces an error on failure', () => {
      svc.createCreditBureau.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  it('confirmDelete deletes then reloads', () => {
    const c = make();
    c.openDelete(bureau({ id: 'cb1' }));
    c.confirmDelete();
    expect(svc.deleteCreditBureau).toHaveBeenCalledWith('cb1');
    expect(svc.listCreditBureaus).toHaveBeenCalledTimes(2);
  });

  describe('confirmToggle', () => {
    it('deactivates an active bureau', () => {
      const c = make();
      c.openToggle(bureau({ id: 'cb1', active: true }));
      c.confirmToggle();
      expect(svc.deactivateCreditBureau).toHaveBeenCalledWith('cb1');
      expect(svc.listCreditBureaus).toHaveBeenCalledTimes(2);
    });

    it('activates an inactive bureau', () => {
      const c = make();
      c.openToggle(bureau({ id: 'cb1', active: false }));
      c.confirmToggle();
      expect(svc.activateCreditBureau).toHaveBeenCalledWith('cb1');
    });

    it('surfaces an error on failure', () => {
      svc.deactivateCreditBureau.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openToggle(bureau({ id: 'cb1', active: true }));
      c.confirmToggle();
      expect(c.modalError).toBe('Action failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('saveMapping', () => {
    it('creates a mapping then refreshes the bureau mappings', () => {
      const c = make();
      c.openAddMapping('cb1');
      c.mappingForm = { loanProductId: 'lp1', creditCheckMandatory: true };
      c.saveMapping();
      expect(svc.createCreditBureauMapping).toHaveBeenCalledWith('cb1', c.mappingForm);
      expect(svc.listCreditBureauMappings).toHaveBeenCalledWith('cb1');
      expect(c.activeModal).toBeNull();
    });

    it('does nothing without a bureau id', () => {
      const c = make();
      c.mappingBureauId = null;
      c.saveMapping();
      expect(svc.createCreditBureauMapping).not.toHaveBeenCalled();
    });

    it('surfaces an error on failure', () => {
      svc.createCreditBureauMapping.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openAddMapping('cb1');
      c.saveMapping();
      expect(c.modalError).toBe('Save failed. Please try again.');
    });
  });

  it('deleteMapping deletes then reloads that bureau', () => {
    const c = make();
    c.deleteMapping('cb1', 'm1');
    expect(svc.deleteCreditBureauMapping).toHaveBeenCalledWith('cb1', 'm1');
    expect(svc.listCreditBureauMappings).toHaveBeenCalledWith('cb1');
  });
});
