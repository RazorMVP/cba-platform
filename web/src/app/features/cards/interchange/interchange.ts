import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, InterchangeRate, InterchangeRateRequest, SchemeFee, SchemeType, CardType, TransactionType, ChannelType } from '../cards.service';

@Component({
  selector: 'app-interchange',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interchange.html',
  styleUrl: './interchange.scss',
})
export class InterchangeComponent implements OnInit {
  private readonly svc = inject(CardsService);

  rates: InterchangeRate[] = [];
  fees: SchemeFee[] = [];
  loading = true;
  activeTab: 'rates' | 'fees' = 'rates';
  showModal = false;
  editId: string | null = null;
  schemeFilter: SchemeType | '' = '';

  form: InterchangeRateRequest = {
    scheme: 'VISA', cardType: 'DEBIT', transactionType: 'PURCHASE', channel: 'CARD_PRESENT',
    ratePercent: 1.5, fixedFee: 0, currencyCode: 'USD', effectiveFrom: '',
  };

  readonly schemes: SchemeType[] = ['VISA', 'MASTERCARD', 'VERVE', 'AFRIGO', 'UNIONPAY'];
  readonly cardTypes: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT'];
  readonly txnTypes: TransactionType[] = ['PURCHASE', 'CASH', 'REFUND'];
  readonly channels: ChannelType[] = ['CARD_PRESENT', 'CNP'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listRates().subscribe({ next: r => { this.rates = r; this.loading = false; }, error: () => { this.loading = false; } });
    this.svc.listSchemeFees().subscribe({ next: f => this.fees = f, error: () => {} });
  }

  get filteredRates(): InterchangeRate[] {
    return this.schemeFilter ? this.rates.filter(r => r.scheme === this.schemeFilter) : this.rates;
  }

  openCreate(): void { this.editId = null; this.form = { scheme: 'VISA', cardType: 'DEBIT', transactionType: 'PURCHASE', channel: 'CARD_PRESENT', ratePercent: 1.5, fixedFee: 0, currencyCode: 'USD', effectiveFrom: '' }; this.showModal = true; }
  openEdit(r: InterchangeRate): void {
    this.editId = r.id;
    this.form = { scheme: r.scheme, cardType: r.cardType, transactionType: r.transactionType, channel: r.channel, ratePercent: r.ratePercent, fixedFee: r.fixedFee, currencyCode: r.currencyCode, effectiveFrom: r.effectiveFrom, effectiveTo: r.effectiveTo ?? undefined, mccCategory: r.mccCategory ?? undefined };
    this.showModal = true;
  }
  closeModal(): void { this.showModal = false; }

  submit(): void {
    const obs$ = this.editId ? this.svc.updateRate(this.editId, this.form) : this.svc.createRate(this.form);
    obs$.subscribe({ next: () => { this.closeModal(); this.load(); } });
  }

  deleteRate(id: string): void { this.svc.deleteRate(id).subscribe({ next: () => this.load() }); }

  totalFeePercent(amount: number, rate: InterchangeRate): number { return rate.ratePercent + (rate.fixedFee / amount * 100); }
}
