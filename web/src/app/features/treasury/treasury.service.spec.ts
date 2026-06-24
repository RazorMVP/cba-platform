import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { TreasuryService } from './treasury.service';

describe('TreasuryService', () => {
  let service: TreasuryService;
  let api: Record<'get' | 'post' | 'put' | 'delete' | 'command', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
      command: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [TreasuryService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(TreasuryService);
  });

  it('placements CRUD + command route correctly', () => {
    service.listPlacements().subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/placements');
    service.getPlacement('p1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/placements/p1');
    service.commandPlacement('p1', 'activate').subscribe();
    expect(api.command).toHaveBeenCalledWith('/treasury/placements/p1', 'activate');
    service.deletePlacement('p1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/treasury/placements/p1');
  });

  it('interbank positions CRUD + command route correctly', () => {
    service.listPositions().subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/positions');
    service.commandPosition('x1', 'settle').subscribe();
    expect(api.command).toHaveBeenCalledWith('/treasury/positions/x1', 'settle');
  });

  it('liquidity reads embed currency/days in the URL string', () => {
    service.getCashFlowForecast('USD').subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/liquidity/cashflow?currency=USD&days=30');
    service.getCashFlowForecast('KES', 7).subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/liquidity/cashflow?currency=KES&days=7');
    service.getLiquiditySnapshots('USD', 10).subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/liquidity/snapshots?currency=USD&limit=10');
  });

  it('reserve requirements CRUD route correctly', () => {
    service.listReserveRequirements().subscribe();
    expect(api.get).toHaveBeenCalledWith('/treasury/liquidity/reserves');
    const req = { currencyCode: 'USD', minimumBalance: 1000 };
    service.createReserveRequirement(req).subscribe();
    expect(api.post).toHaveBeenCalledWith('/treasury/liquidity/reserves', req);
    service.deleteReserveRequirement('rr1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/treasury/liquidity/reserves/rr1');
  });

  it('takeSnapshot posts an empty body', () => {
    service.takeSnapshot().subscribe();
    expect(api.post).toHaveBeenCalledWith('/treasury/liquidity/snapshots/take', {});
  });
});
