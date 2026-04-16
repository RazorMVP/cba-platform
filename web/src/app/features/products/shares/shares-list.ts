import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, takeUntil } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ProductService, ShareProduct } from '../product.service';

@Component({
  selector: 'app-shares-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './shares-list.html',
  styleUrl: './shares-list.scss',
})
export class SharesListComponent implements OnInit, OnDestroy {
  private readonly svc      = inject(ProductService);
  private readonly destroy$ = new Subject<void>();
  private readonly search$  = new Subject<string>();

  products: ShareProduct[] = [];
  filtered: ShareProduct[] = [];
  loading = true;

  searchQuery = '';
  activeOnly  = false;

  page     = 0;
  pageSize = 15;

  ngOnInit(): void {
    this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      takeUntil(this.destroy$),
    ).subscribe(q => this.applyFilter(q));

    this.load();
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  load(): void {
    this.loading = true;
    this.svc.listShareProducts(this.activeOnly).pipe(takeUntil(this.destroy$)).subscribe({
      next: list => { this.products = list; this.applyFilter(this.searchQuery); this.loading = false; },
      error: ()   => { this.loading = false; },
    });
  }

  onSearch(q: string):    void { this.search$.next(q); }
  onActiveFilterChange(): void { this.load(); }

  applyFilter(q: string): void {
    const term = q.trim().toLowerCase();
    this.filtered = this.products.filter(p =>
      !term ||
      p.name.toLowerCase().includes(term) ||
      p.shortName.toLowerCase().includes(term) ||
      (p.description ?? '').toLowerCase().includes(term),
    );
    this.page = 0;
  }

  get totalPages(): number { return Math.max(1, Math.ceil(this.filtered.length / this.pageSize)); }
  get startRow():   number { return this.filtered.length === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.filtered.length); }
  get pageItems():  ShareProduct[] {
    return this.filtered.slice(this.page * this.pageSize, (this.page + 1) * this.pageSize);
  }

  prevPage(): void { if (this.page > 0) this.page--; }
  nextPage(): void { if (this.page < this.totalPages - 1) this.page++; }

  productVariant(p: ShareProduct): 'success' | 'neutral' {
    return p.active ? 'success' : 'neutral';
  }
}
