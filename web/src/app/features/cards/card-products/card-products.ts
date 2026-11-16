import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, CardProduct, CardProductRequest, CardType } from '../cards.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-card-products',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './card-products.html',
  styleUrl: './card-products.scss',
})
export class CardProductsComponent implements OnInit {
  private readonly svc = inject(CardsService);

  products: CardProduct[] = [];
  loading = true;
  showModal = false;
  /** The product being edited; null = creating a new one. */
  editing: CardProduct | null = null;
  saving = false;
  error = '';
  form: CardProductRequest = this.blank();
  readonly types: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listProducts().subscribe({ next: p => { this.products = p; this.loading = false; }, error: () => { this.loading = false; } });
  }

  openCreate(): void { this.editing = null; this.error = ''; this.form = this.blank(); this.showModal = true; }

  /** Edit a product's name, daily limit and — for a prepaid product — its wallet limits (backlog 15c). */
  openEdit(p: CardProduct): void {
    this.editing = p;
    this.error = '';
    this.form = {
      name: p.name, cardType: p.cardType, binRangeStart: p.binRangeStart, binRangeEnd: p.binRangeEnd,
      defaultDailyLimit: p.defaultDailyLimit,
      maxWalletBalance: p.maxWalletBalance ?? null, dailyWalletLoad: p.dailyWalletLoad ?? null,
    };
    this.showModal = true;
  }

  closeModal(): void { this.showModal = false; this.editing = null; }

  submit(): void {
    const prepaid = this.form.cardType === 'PREPAID';
    const maxWalletBalance = prepaid ? this.limit(this.form.maxWalletBalance) : null;
    const dailyWalletLoad = prepaid ? this.limit(this.form.dailyWalletLoad) : null;
    const done = {
      next: () => { this.saving = false; this.closeModal(); this.load(); },
      error: (e: { error?: { errors?: { message?: string }[] } }) => {
        this.saving = false;
        this.error = e?.error?.errors?.[0]?.message ?? 'The product could not be saved.';
      },
    };
    this.saving = true;
    this.error = '';
    if (this.editing) {
      this.svc.updateProduct(this.editing.id, { ...this.editing, name: this.form.name,
        defaultDailyLimit: this.form.defaultDailyLimit, maxWalletBalance, dailyWalletLoad }).subscribe(done);
    } else {
      this.svc.createProduct({ ...this.form, maxWalletBalance, dailyWalletLoad }).subscribe(done);
    }
  }

  /** "250,000 max · 50,000 a day"; a blank limit falls back to the wallet currency's default. */
  walletLimits(p: CardProduct): string {
    if (p.cardType !== 'PREPAID') return '—';
    if (p.maxWalletBalance == null && p.dailyWalletLoad == null) return 'Currency default';
    const fmt = (v: number | null | undefined) => v == null ? 'default' : v.toLocaleString('en-US');
    return `${fmt(p.maxWalletBalance)} max · ${fmt(p.dailyWalletLoad)} a day`;
  }

  typeClass(t: CardType): string { return ({ DEBIT: 'chip-info', PREPAID: 'chip-warn', CREDIT: 'chip-primary' } as Record<string, string>)[t]; }

  /** A blank or non-positive field means "use the currency default" (null). */
  private limit(v: number | null | undefined): number | null {
    return v == null || !(Number(v) > 0) ? null : Number(v);
  }

  private blank(): CardProductRequest {
    return { name: '', cardType: 'DEBIT', binRangeStart: '', binRangeEnd: '', defaultDailyLimit: 500000,
      maxWalletBalance: null, dailyWalletLoad: null };
  }
}
