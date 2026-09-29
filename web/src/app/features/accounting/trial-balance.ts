import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountingService, TrialBalanceResponse } from './accounting.service';

@Component({
  selector: 'app-trial-balance',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './trial-balance.html',
  styleUrl: './trial-balance.scss',
})
export class TrialBalanceComponent implements OnInit {
  private readonly svc = inject(AccountingService);

  report: TrialBalanceResponse | null = null;
  loading = false;
  error = '';

  fromDate = this.firstOfMonth();
  toDate   = this.today();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (!this.fromDate || !this.toDate) return;
    this.loading = true;
    this.error   = '';
    this.report  = null;
    this.svc.getTrialBalance(this.fromDate, this.toDate).subscribe({
      next:  res  => { this.report = res; this.loading = false; },
      error: ()   => { this.error = 'Failed to load trial balance.'; this.loading = false; },
    });
  }

  exportCsv(): void {
    if (!this.report) return;
    const header = 'Currency,GL Code,Account Name,Account Type,Opening Balance,Debit Movement,Credit Movement,Closing Balance\n';
    const rows = this.report.rows.map(r =>
      `"${r.currencyCode}","${r.glCode}","${r.accountName}","${r.accountType}",${r.openingBalance},${r.debitMovement},${r.creditMovement},${r.closingBalance}`
    ).join('\n');
    // One totals line per currency: amounts in different currencies are never added together.
    const totals = this.report.currencies.map(c =>
      `"${c.currencyCode}",,TOTALS,,,${c.totalDebitMovement},${c.totalCreditMovement},`
    ).join('\n');
    const blob = new Blob([header + rows + '\n' + totals], { type: 'text/csv' });
    const a    = document.createElement('a');
    a.href     = URL.createObjectURL(blob);
    a.download = `trial-balance-${this.fromDate}-to-${this.toDate}.csv`;
    a.click();
  }

  /** The rows in one currency. */
  rowsFor(currencyCode: string): TrialBalanceResponse['rows'] {
    return this.report ? this.report.rows.filter(r => r.currencyCode === currencyCode) : [];
  }

  /** One currency's rows, grouped by account type in chart order. */
  groupedRows(currencyCode: string): { type: string; rows: TrialBalanceResponse['rows'] }[] {
    if (!this.report) return [];
    const order = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'];
    const map   = new Map<string, TrialBalanceResponse['rows']>();
    for (const r of this.rowsFor(currencyCode)) {
      const list = map.get(r.accountType) ?? [];
      list.push(r);
      map.set(r.accountType, list);
    }
    return order
      .filter(t => map.has(t))
      .map(t => ({ type: t, rows: map.get(t)! }));
  }

  subtotal(rows: TrialBalanceResponse['rows'], field: keyof Pick<TrialBalanceResponse['rows'][0], 'openingBalance' | 'debitMovement' | 'creditMovement' | 'closingBalance'>): number {
    return rows.reduce((sum, r) => sum + Number(r[field]), 0);
  }

  private today(): string {
    return new Date().toISOString().substring(0, 10);
  }

  private firstOfMonth(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }
}
