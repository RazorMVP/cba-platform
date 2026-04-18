import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';

export interface TreasuryPlacement {
  id: string;
  reference: string;
  counterpartyName: string;
  counterpartyBic?: string;
  placementType: 'FIXED_DEPOSIT' | 'TREASURY_BILL' | 'BOND' | 'CALL_MONEY' | 'REPO';
  principal: number;
  interestRate: number;
  currencyCode: string;
  startDate: string;
  maturityDate: string;
  status: 'PENDING' | 'ACTIVE' | 'MATURED' | 'CANCELLED';
  expectedReturn?: number;
  actualReturn?: number;
  glSourceAccount?: string;
  glIncomeAccount?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TreasuryInterbankPosition {
  id: string;
  reference: string;
  counterpartyName: string;
  counterpartyBic?: string;
  direction: 'LENDING' | 'BORROWING';
  amount: number;
  currencyCode: string;
  interestRate: number;
  startDate: string;
  maturityDate?: string;
  status: 'ACTIVE' | 'SETTLED' | 'CANCELLED';
  settlementGl?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlacementRequest {
  reference: string;
  counterpartyName: string;
  counterpartyBic?: string;
  placementType: string;
  principal: number;
  interestRate: number;
  currencyCode: string;
  startDate: string;
  maturityDate: string;
  expectedReturn?: number;
  notes?: string;
}

export interface InterbankRequest {
  reference: string;
  counterpartyName: string;
  counterpartyBic?: string;
  direction: string;
  amount: number;
  currencyCode: string;
  interestRate: number;
  startDate: string;
  maturityDate?: string;
  notes?: string;
}

@Injectable({ providedIn: 'root' })
export class TreasuryService {
  private readonly api = inject(ApiService);

  // ── Placements ──────────────────────────────────────────────────────────────

  listPlacements(): Observable<TreasuryPlacement[]> {
    return this.api.get<TreasuryPlacement[]>('/treasury/placements');
  }

  getPlacement(id: string): Observable<TreasuryPlacement> {
    return this.api.get<TreasuryPlacement>(`/treasury/placements/${id}`);
  }

  createPlacement(req: PlacementRequest): Observable<TreasuryPlacement> {
    return this.api.post<TreasuryPlacement>('/treasury/placements', req);
  }

  updatePlacement(id: string, req: PlacementRequest): Observable<TreasuryPlacement> {
    return this.api.put<TreasuryPlacement>(`/treasury/placements/${id}`, req);
  }

  commandPlacement(id: string, command: string): Observable<TreasuryPlacement> {
    return this.api.command<TreasuryPlacement>(`/treasury/placements/${id}`, command);
  }

  deletePlacement(id: string): Observable<void> {
    return this.api.delete<void>(`/treasury/placements/${id}`);
  }

  // ── Interbank Positions ────────────────────────────────────────────────────

  listPositions(): Observable<TreasuryInterbankPosition[]> {
    return this.api.get<TreasuryInterbankPosition[]>('/treasury/positions');
  }

  getPosition(id: string): Observable<TreasuryInterbankPosition> {
    return this.api.get<TreasuryInterbankPosition>(`/treasury/positions/${id}`);
  }

  createPosition(req: InterbankRequest): Observable<TreasuryInterbankPosition> {
    return this.api.post<TreasuryInterbankPosition>('/treasury/positions', req);
  }

  updatePosition(id: string, req: InterbankRequest): Observable<TreasuryInterbankPosition> {
    return this.api.put<TreasuryInterbankPosition>(`/treasury/positions/${id}`, req);
  }

  commandPosition(id: string, command: string): Observable<TreasuryInterbankPosition> {
    return this.api.command<TreasuryInterbankPosition>(`/treasury/positions/${id}`, command);
  }

  deletePosition(id: string): Observable<void> {
    return this.api.delete<void>(`/treasury/positions/${id}`);
  }

  // ── Liquidity ────────────────────────────────────────────────────────────────

  getLiquidityPositions(): Observable<LiquidityPosition[]> {
    return this.api.get<LiquidityPosition[]>('/treasury/liquidity/positions');
  }

  getLiquidityPosition(currency: string): Observable<LiquidityPosition> {
    return this.api.get<LiquidityPosition>(`/treasury/liquidity/positions/${currency}`);
  }

  getCashFlowForecast(currency: string, days = 30): Observable<CashFlowEntry[]> {
    return this.api.get<CashFlowEntry[]>(`/treasury/liquidity/cashflow?currency=${currency}&days=${days}`);
  }

  listReserveRequirements(): Observable<ReserveRequirement[]> {
    return this.api.get<ReserveRequirement[]>('/treasury/liquidity/reserves');
  }

  createReserveRequirement(req: ReserveRequest): Observable<ReserveRequirement> {
    return this.api.post<ReserveRequirement>('/treasury/liquidity/reserves', req);
  }

  updateReserveRequirement(id: string, req: ReserveRequest): Observable<ReserveRequirement> {
    return this.api.put<ReserveRequirement>(`/treasury/liquidity/reserves/${id}`, req);
  }

  deleteReserveRequirement(id: string): Observable<void> {
    return this.api.delete<void>(`/treasury/liquidity/reserves/${id}`);
  }

  getLiquiditySnapshots(currency: string, limit = 30): Observable<LiquiditySnapshot[]> {
    return this.api.get<LiquiditySnapshot[]>(`/treasury/liquidity/snapshots?currency=${currency}&limit=${limit}`);
  }

  takeSnapshot(): Observable<void> {
    return this.api.post<void>('/treasury/liquidity/snapshots/take', {});
  }
}

// ── Liquidity interfaces ─────────────────────────────────────────────────────

export interface LiquidityPosition {
  currency: string;
  cashOnHand: number;
  placementsDeployed: number;
  interbankLending: number;
  interbankBorrowing: number;
  netLiquidityPosition: number;
  reserveRequirement: number;
  surplusDeficit: number;
  alertLevel: 'OK' | 'WARN' | 'BREACH';
  asOfDate: string;
}

export interface CashFlowEntry {
  date: string;
  type: string;
  reference: string;
  amount: number;
  currency: string;
  direction: 'INFLOW' | 'OUTFLOW';
}

export interface ReserveRequirement {
  id: string;
  currencyCode: string;
  minimumBalance: number;
  minimumRatioPercent?: number;
  alertThresholdPercent?: number;
  regulatoryReference?: string;
  active: boolean;
}

export interface ReserveRequest {
  currencyCode: string;
  minimumBalance: number;
  minimumRatioPercent?: number;
  alertThresholdPercent?: number;
  regulatoryReference?: string;
}

export interface LiquiditySnapshot {
  id: string;
  snapshotDate: string;
  currencyCode: string;
  cashOnHand: number;
  placementsDeployed: number;
  interbankLending: number;
  interbankBorrowing: number;
  netLiquidityPosition: number;
  reserveRequirement?: number;
  surplusDeficit?: number;
  createdAt: string;
}
