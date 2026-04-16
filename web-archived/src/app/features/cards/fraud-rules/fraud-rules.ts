import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, FraudRule, UpdateFraudRuleRequest } from '../cards.service';

@Component({
  selector: 'app-fraud-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fraud-rules.html',
  styleUrl: './fraud-rules.scss',
})
export class FraudRulesComponent implements OnInit {
  private readonly svc = inject(CardsService);

  rules: FraudRule[] = [];
  loading = true;
  editingId: string | null = null;
  editForm: UpdateFraudRuleRequest = { weight: 0, enabled: true, params: {} };
  paramsJson = '';

  readonly ruleDescriptions: Record<string, string> = {
    VELOCITY_LIMIT:        'More than N transactions in Y minutes on same card',
    SINGLE_AMOUNT_LIMIT:   'Single transaction exceeds per-transaction limit',
    BLOCKED_COUNTRY:       'Transaction from a blocked country code',
    BLOCKED_MCC:           'Merchant Category Code on the blocked list',
    DUPLICATE_TRANSACTION: 'Same amount + merchant within 2 minutes',
    CNP_DEBIT:             'Card-not-present transaction on a debit card',
    OUTSIDE_HOURS:         'Transaction outside permitted hours for this card product',
    CARD_EXPIRED:          'Card expiry date has passed — hard block',
    CARD_BLOCKED:          'Card status is BLOCKED or CANCELLED — hard block',
    PIN_RETRY_EXCEEDED:    'PIN retry counter ≥ 3 — hard block',
  };

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listFraudRules().subscribe({ next: r => { this.rules = r; this.loading = false; }, error: () => { this.loading = false; } });
  }

  startEdit(rule: FraudRule): void {
    this.editingId = rule.id;
    this.editForm = { weight: rule.weight, enabled: rule.enabled, params: { ...rule.params } };
    this.paramsJson = JSON.stringify(rule.params, null, 2);
  }

  cancelEdit(): void { this.editingId = null; }

  saveEdit(id: string): void {
    try { this.editForm.params = JSON.parse(this.paramsJson); } catch { return; }
    this.svc.updateFraudRule(id, this.editForm).subscribe({ next: () => { this.editingId = null; this.load(); } });
  }

  isHardBlock(ruleId: string): boolean { return ['CARD_EXPIRED', 'CARD_BLOCKED', 'PIN_RETRY_EXCEEDED'].includes(ruleId); }

  scoreColor(weight: number): string {
    if (weight >= 70) return 'high';
    if (weight >= 30) return 'medium';
    return 'low';
  }
}
