import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, BinRange, BinRangeRequest, SchemeType, CardType } from '../cards.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-bin-management',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './bin-management.html',
  styleUrl: './bin-management.scss',
})
export class BinManagementComponent implements OnInit {
  private readonly svc = inject(CardsService);

  bins: BinRange[] = [];
  loading = true;
  showModal = false;
  editId: string | null = null;
  form: BinRangeRequest = { binStart: '', binEnd: '', scheme: 'VISA', productType: '', cardType: 'DEBIT', countryCode: '', currencyCode: '' };

  readonly schemes: SchemeType[] = ['VISA', 'MASTERCARD', 'VERVE', 'AFRIGO', 'UNIONPAY'];
  readonly cardTypes: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listBins().subscribe({ next: b => { this.bins = b; this.loading = false; }, error: () => { this.loading = false; } });
  }

  openCreate(): void { this.editId = null; this.form = { binStart: '', binEnd: '', scheme: 'VISA', productType: '', cardType: 'DEBIT', countryCode: '', currencyCode: '' }; this.showModal = true; }
  openEdit(b: BinRange): void { this.editId = b.id; this.form = { binStart: b.binStart, binEnd: b.binEnd, scheme: b.scheme, productType: b.productType, cardType: b.cardType, countryCode: b.countryCode, currencyCode: b.currencyCode }; this.showModal = true; }
  closeModal(): void { this.showModal = false; }

  submit(): void {
    const obs$ = this.editId ? this.svc.updateBin(this.editId, this.form) : this.svc.createBin(this.form);
    obs$.subscribe({ next: () => { this.closeModal(); this.load(); } });
  }

  delete(id: string): void { this.svc.deleteBin(id).subscribe({ next: () => this.load() }); }

  schemeColor(s: SchemeType): string {
    return ({ VISA: '#1a1f71', MASTERCARD: '#eb001b', VERVE: '#006400', AFRIGO: '#ff6600', UNIONPAY: '#c0102c' } as Record<string, string>)[s] ?? '#374151';
  }
}
