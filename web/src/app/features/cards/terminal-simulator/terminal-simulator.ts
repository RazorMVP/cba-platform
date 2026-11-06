import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, SimulateRequest, SimulateResponse, EntryMode } from '../cards.service';

type TxnType = 'purchase' | 'withdrawal' | 'balance' | 'reversal' | 'signon' | 'echo';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-terminal-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './terminal-simulator.html',
  styleUrl: './terminal-simulator.scss',
})
export class TerminalSimulatorComponent {
  private readonly svc = inject(CardsService);

  txnType: TxnType = 'purchase';
  entryMode: EntryMode = 'CHIP';
  request: SimulateRequest = {
    cardNumber: '', expiryDate: '', amount: 10000, currency: '840',
    terminalId: 'TERM0001', merchantId: 'MERCHANT00001  ',
    merchantName: 'Test Merchant Ltd       ', entryMode: 'CHIP', pinBlock: '',
  };

  /** Send DE6/DE51/DE10: the merchant's currency differs from the card's (backlog 17). */
  billing = false;
  billingAmount: number | null = null;
  billingCurrency = '840';
  conversionRate = '';

  response: SimulateResponse | null = null;
  loading = false;
  showHexDump = false;

  readonly txnTypes: { value: TxnType; label: string }[] = [
    { value: 'purchase',   label: 'Purchase (0100)' },
    { value: 'withdrawal', label: 'Cash Withdrawal (0200)' },
    { value: 'balance',    label: 'Balance Enquiry (0100)' },
    { value: 'reversal',   label: 'Reversal (0400)' },
    { value: 'signon',     label: 'Network Sign-On (0800)' },
    { value: 'echo',       label: 'Echo Test (0800)' },
  ];

  readonly entryModes: EntryMode[] = ['CHIP', 'SWIPE', 'CONTACTLESS'];

  readonly currencies = [
    { code: '840', label: 'USD ($)' },
    { code: '404', label: 'KES (KSh)' },
    { code: '288', label: 'GHS (GH₵)' },
    { code: '566', label: 'NGN (₦)' },
    { code: '978', label: 'EUR (€)' },
    { code: '826', label: 'GBP (£)' },
  ];

  send(): void {
    this.loading = true;
    this.response = null;
    const req: SimulateRequest = { ...this.request, entryMode: this.entryMode };
    if (this.billing && this.needsAmount && this.billingAmount != null) {
      req.billingAmount = this.billingAmount;
      req.billingCurrency = this.billingCurrency;
      if (this.conversionRate.trim()) req.conversionRate = this.conversionRate.trim();
    }

    const obs$ = this.txnType === 'purchase'    ? this.svc.simulatePurchase(req)
               : this.txnType === 'withdrawal'  ? this.svc.simulateWithdrawal(req)
               : this.txnType === 'balance'     ? this.svc.simulateBalance(req)
               : this.txnType === 'reversal'    ? this.svc.simulateReversal(req)
               : this.txnType === 'signon'      ? this.svc.networkSignOn()
               :                                  this.svc.networkEcho();

    obs$.subscribe({
      next:  r => { this.response = r; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  get approved(): boolean { return this.response?.responseCode === '00'; }

  get needsCardFields(): boolean { return !['signon', 'echo'].includes(this.txnType); }
  get needsAmount(): boolean { return ['purchase', 'withdrawal'].includes(this.txnType); }
}
