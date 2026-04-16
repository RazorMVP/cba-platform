import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, CardProduct, CardProductRequest, CardType } from '../cards.service';

@Component({
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
  form: CardProductRequest = { name: '', cardType: 'DEBIT', binRangeStart: '', binRangeEnd: '', defaultDailyLimit: 500000 };
  readonly types: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listProducts().subscribe({ next: p => { this.products = p; this.loading = false; }, error: () => { this.loading = false; } });
  }

  openCreate(): void { this.form = { name: '', cardType: 'DEBIT', binRangeStart: '', binRangeEnd: '', defaultDailyLimit: 500000 }; this.showModal = true; }
  closeModal(): void { this.showModal = false; }

  submit(): void {
    this.svc.createProduct(this.form).subscribe({ next: () => { this.closeModal(); this.load(); } });
  }

  typeClass(t: CardType): string { return ({ DEBIT: 'chip-info', PREPAID: 'chip-warn', CREDIT: 'chip-primary' } as Record<string, string>)[t]; }
}
