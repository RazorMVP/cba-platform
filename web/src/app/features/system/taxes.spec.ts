import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TaxesComponent } from './taxes';
import { SystemService, TaxComponent, TaxGroup } from './system.service';

type Svc = Record<
  'listTaxComponents' | 'createTaxComponent' | 'updateTaxComponent' |
  'listTaxGroups' | 'createTaxGroup' | 'updateTaxGroup',
  ReturnType<typeof vi.fn>
>;

function comp(over: Partial<TaxComponent> = {}): TaxComponent {
  return { id: 'tc1', name: 'VAT', percentage: 16, startDate: '2026-01-01', creditAccountId: null, debitAccountId: null, ...over };
}
function group(over: Partial<TaxGroup> = {}): TaxGroup {
  return { id: 'tg1', name: 'Standard', components: [{ taxComponentId: 'tc1', taxComponentName: 'VAT', startDate: '2026-01-01' }], ...over };
}

describe('TaxesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listTaxComponents: vi.fn().mockReturnValue(of([comp()])),
      createTaxComponent: vi.fn().mockReturnValue(of(comp({ id: 'tc2', name: 'New' }))),
      updateTaxComponent: vi.fn().mockReturnValue(of(comp({ id: 'tc1', name: 'Edited' }))),
      listTaxGroups: vi.fn().mockReturnValue(of([group()])),
      createTaxGroup: vi.fn().mockReturnValue(of(group({ id: 'tg2', name: 'New' }))),
      updateTaxGroup: vi.fn().mockReturnValue(of(group({ id: 'tg1', name: 'Edited' }))),
    };
    TestBed.configureTestingModule({
      imports: [TaxesComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TaxesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads components and groups on init', () => {
    const c = make();
    expect(svc.listTaxComponents).toHaveBeenCalled();
    expect(svc.listTaxGroups).toHaveBeenCalled();
    expect(c.components).toHaveLength(1);
    expect(c.groups).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when component loading fails', () => {
    svc.listTaxComponents.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load tax components.');
  });

  describe('component CRUD', () => {
    it('submitComponent requires name and startDate', () => {
      const c = make();
      c.openCreateComponent();
      c.componentForm.name = '';
      c.submitComponent();
      expect(svc.createTaxComponent).not.toHaveBeenCalled();
    });

    it('creates a component and appends it, dropping empty GL ids', () => {
      const c = make();
      c.components = [];
      c.openCreateComponent();
      c.componentForm = { name: 'New', percentage: 5, startDate: '2026-02-01', creditAccountId: '', debitAccountId: '' };
      c.submitComponent();
      expect(svc.createTaxComponent).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'New', creditAccountId: undefined, debitAccountId: undefined }),
      );
      expect(c.components).toHaveLength(1);
      expect(c.activeModal).toBeNull();
    });

    it('updates a component in place', () => {
      const c = make();
      c.components = [comp({ id: 'tc1' })];
      c.openEditComponent(c.components[0]);
      c.submitComponent();
      expect(svc.updateTaxComponent).toHaveBeenCalledWith('tc1', expect.anything());
      expect(c.components[0].name).toBe('Edited');
    });

    it('surfaces an error on failure', () => {
      svc.createTaxComponent.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateComponent();
      c.componentForm = { name: 'N', percentage: 1, startDate: '2026-01-01', creditAccountId: '', debitAccountId: '' };
      c.submitComponent();
      expect(c.modalError).toBe('Failed to save tax component.');
    });
  });

  describe('group CRUD', () => {
    it('group rows can be added and pruned to at least one', () => {
      const c = make();
      c.openCreateGroup();
      c.addGroupRow();
      expect(c.groupComponentRows).toHaveLength(2);
      c.removeGroupRow(0);
      expect(c.groupComponentRows).toHaveLength(1);
      c.removeGroupRow(0);
      expect(c.groupComponentRows).toHaveLength(1);
    });

    it('submitGroup requires a name', () => {
      const c = make();
      c.openCreateGroup();
      c.groupName = '';
      c.submitGroup();
      expect(svc.createTaxGroup).not.toHaveBeenCalled();
    });

    it('creates a group, filtering out rows without a component id', () => {
      const c = make();
      c.groups = [];
      c.openCreateGroup();
      c.groupName = 'New';
      c.groupComponentRows = [
        { taxComponentId: 'tc1', taxComponentName: 'VAT', startDate: '2026-01-01' },
        { taxComponentId: '', taxComponentName: '', startDate: '' },
      ];
      c.submitGroup();
      expect(svc.createTaxGroup).toHaveBeenCalledWith({
        name: 'New',
        components: [{ taxComponentId: 'tc1', startDate: '2026-01-01' }],
      });
      expect(c.groups).toHaveLength(1);
    });

    it('updates a group in place', () => {
      const c = make();
      c.groups = [group({ id: 'tg1' })];
      c.openEditGroup(c.groups[0]);
      c.submitGroup();
      expect(svc.updateTaxGroup).toHaveBeenCalledWith('tg1', expect.anything());
      expect(c.groups[0].name).toBe('Edited');
    });
  });

  it('componentName resolves an id or falls back to the id', () => {
    const c = make();
    c.components = [comp({ id: 'tc1', name: 'VAT' })];
    expect(c.componentName('tc1')).toBe('VAT');
    expect(c.componentName('missing')).toBe('missing');
  });
});
