import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CardListComponent } from './card-list';
import { CardsService, Card } from '../cards.service';

type Svc = Record<'listCards' | 'issueCard', ReturnType<typeof vi.fn>>;

function card(over: Partial<Card> = {}): Card {
  return {
    id: 'cd1', panPrefix: '457312', panSuffix: '1234', expiryDate: '2612',
    cardType: 'DEBIT', status: 'ACTIVE', virtualFlag: false, customerId: 'cust-1',
    linkedEntityId: 'acc-1', productId: 'p1', productName: 'Classic Debit',
    pinRetryCount: 0, createdAt: '2026-01-01', updatedAt: '2026-01-01', ...over,
  };
}

describe('CardListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listCards: vi.fn().mockReturnValue(of([card()])),
      issueCard: vi.fn().mockReturnValue(of(card({ id: 'cd2' }))),
    };
    TestBed.configureTestingModule({
      imports: [CardListComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CardListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads cards on init and clears loading', () => {
    const c = make();
    expect(svc.listCards).toHaveBeenCalled();
    expect(c.cards).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false even when load fails', () => {
    svc.listCards.mockReturnValue(throwError(() => new Error('boom')));
    const c = make();
    expect(c.loading).toBe(false);
    expect(c.cards).toHaveLength(0);
  });

  describe('applyFilter', () => {
    it('matches PAN suffix, customer id and product name', () => {
      const c = make();
      c.cards = [
        card({ id: 'a', panSuffix: '9999', customerId: 'alpha', productName: 'Gold' }),
        card({ id: 'b', panSuffix: '1111', customerId: 'beta', productName: 'Silver' }),
      ];
      c.onSearch('9999');
      expect(c.filtered.map(x => x.id)).toEqual(['a']);
      c.onSearch('beta');
      expect(c.filtered.map(x => x.id)).toEqual(['b']);
      c.onSearch('silver');
      expect(c.filtered.map(x => x.id)).toEqual(['b']);
      c.onSearch('');
      expect(c.filtered).toHaveLength(2);
    });

    it('combines status and type filters', () => {
      const c = make();
      c.cards = [
        card({ id: 'a', status: 'ACTIVE', cardType: 'DEBIT' }),
        card({ id: 'b', status: 'BLOCKED', cardType: 'DEBIT' }),
        card({ id: 'c', status: 'ACTIVE', cardType: 'CREDIT' }),
      ];
      c.statusFilter = 'ACTIVE';
      c.typeFilter = 'DEBIT';
      c.applyFilter();
      expect(c.filtered.map(x => x.id)).toEqual(['a']);
    });
  });

  describe('issue modal', () => {
    it('opens and resets the form on close', () => {
      const c = make();
      c.openIssue();
      expect(c.showIssueModal).toBe(true);
      c.issueForm.customerId = 'x';
      c.closeIssue();
      expect(c.showIssueModal).toBe(false);
      expect(c.issueForm).toEqual({ customerId: '', productId: '', linkedEntityId: '', virtual: false });
    });

    it('submitIssue calls the service then closes and reloads', () => {
      const c = make();
      c.openIssue();
      c.issueForm = { customerId: 'cust-1', productId: 'p1', linkedEntityId: 'acc-1', virtual: true };
      svc.listCards.mockClear();
      c.submitIssue();
      // closeIssue() resets issueForm in the next callback, so assert on a literal (not the live ref)
      expect(svc.issueCard).toHaveBeenCalledWith({ customerId: 'cust-1', productId: 'p1', linkedEntityId: 'acc-1', virtual: true });
      expect(c.showIssueModal).toBe(false);
      expect(svc.listCards).toHaveBeenCalled();
    });
  });

  it('statusVariant maps statuses to badge variants', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('BLOCKED')).toBe('error');
    expect(c.statusVariant('DISPATCHED')).toBe('warning');
    expect(c.statusVariant('ORDERED')).toBe('info');
    expect(c.statusVariant('EXPIRED')).toBe('neutral');
  });
});
