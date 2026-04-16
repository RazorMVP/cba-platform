import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ProductService, LoanProduct } from '../product.service';

@Component({
  selector: 'app-loan-products-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './loan-products-list.html',
  styleUrl: './loan-products-list.scss',
})
export class LoanProductsListComponent implements OnInit, OnDestroy {
  private readonly svc = inject(ProductService);
  private readonly destroy$ = new Subject<void>();
  private readonly search$ = new Subject<string>();

  products: LoanProduct[] = [];
  filtered: LoanProduct[] = [];
  loading = true;
  searchQuery = '';
  activeOnly = false;

  // Pagination
  page = 0;
  readonly pageSize = 15;

  ngOnInit(): void {
    this.load();

    // Client-side filter (list is not paginated server-side for products)
    this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(q => this.applyFilter(q));
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  load(): void {
    this.loading = true;
    this.svc.listLoanProducts(this.activeOnly).subscribe({
      next: p => { this.products = p; this.applyFilter(this.searchQuery); this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  onSearch(q: string): void { this.search$.next(q); }

  onActiveFilterChange(): void { this.page = 0; this.load(); }

  applyFilter(q: string): void {
    const lower = q.toLowerCase();
    this.filtered = lower
      ? this.products.filter(p =>
          p.name.toLowerCase().includes(lower) ||
          p.shortName.toLowerCase().includes(lower) ||
          (p.description ?? '').toLowerCase().includes(lower)
        )
      : [...this.products];
    this.page = 0;
  }

  // Pagination
  get totalPages(): number { return Math.max(1, Math.ceil(this.filtered.length / this.pageSize)); }
  get startRow(): number   { return this.filtered.length === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow(): number     { return Math.min((this.page + 1) * this.pageSize, this.filtered.length); }
  get pageItems(): LoanProduct[] {
    return this.filtered.slice(this.page * this.pageSize, (this.page + 1) * this.pageSize);
  }
  prevPage(): void { if (this.page > 0) this.page--; }
  nextPage(): void { if (this.page < this.totalPages - 1) this.page++; }

  // Helpers
  repaymentLabel(type: string): string {
    const m: Record<string, string> = {
      ANNUITY: 'Annuity', FLAT: 'Flat', DECLINING_BALANCE: 'Declining Balance',
    };
    return m[type] ?? type;
  }

  interestTypeLabel(type: string): string {
    return type === 'DECLINING_BALANCE' ? 'Declining Bal.' : 'Flat';
  }

  productVariant(p: LoanProduct): 'success' | 'neutral' {
    return p.active ? 'success' : 'neutral';
  }
}
