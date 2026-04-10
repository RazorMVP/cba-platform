import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { ProductService, RecurringDepositProduct, RecurringDepositProductRequest } from '../../product.service';

type DetailSection = 'core' | 'rates' | 'term' | 'frequency' | 'penalty';

@Component({
  selector: 'app-recurring-deposit-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './recurring-deposit-detail.html',
  styleUrl: './recurring-deposit-detail.scss',
})
export class RecurringDepositDetailComponent implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc    = inject(ProductService);

  product: RecurringDepositProduct | null = null;
  loading = true;
  error   = '';
  isNew   = false;

  editMode  = false;
  saving    = false;
  saveError = '';
  form!: RecurringDepositProductRequest;

  showDeactivateConfirm = false;
  deactivating = false;

  activeSection: DetailSection = 'core';

  readonly compoundingPeriods = ['DAILY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'];
  readonly postingPeriods     = ['MONTHLY', 'QUARTERLY', 'BIANNUAL', 'ANNUAL'];
  readonly calcTypes          = ['DAILY_BALANCE', 'AVERAGE_DAILY_BALANCE'];
  readonly termTypes          = ['DAYS', 'WEEKS', 'MONTHS', 'YEARS'];
  readonly depositFrequencies = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'];

  readonly compoundingLabels: Record<string, string> = {
    DAILY: 'Daily', MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', ANNUALLY: 'Annually',
  };
  readonly postingLabels: Record<string, string> = {
    MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', BIANNUAL: 'Bi-Annual', ANNUAL: 'Annual',
  };
  readonly calcTypeLabels: Record<string, string> = {
    DAILY_BALANCE: 'Daily Balance', AVERAGE_DAILY_BALANCE: 'Average Daily Balance',
  };
  readonly termTypeLabels: Record<string, string> = {
    DAYS: 'Days', WEEKS: 'Weeks', MONTHS: 'Months', YEARS: 'Years',
  };
  readonly frequencyLabels: Record<string, string> = {
    DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly',
    QUARTERLY: 'Quarterly', ANNUALLY: 'Annually',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      this.enterEditMode(true);
      return;
    }
    this.svc.getRecurringDepositProduct(id).subscribe({
      next:  p  => { this.product = p; this.loading = false; },
      error: () => { this.error = 'Product not found.'; this.loading = false; },
    });
  }

  enterEditMode(blank = false): void {
    if (blank || !this.product) {
      this.form = {
        name: '',
        shortName: '',
        currencyCode: 'USD',
        nominalAnnualInterestRate: 0,
        minDepositTerm: 1,
        minDepositTermType: 'MONTHS',
        compoundingPeriod: 'MONTHLY',
        postingPeriod: 'MONTHLY',
        calculationType: 'DAILY_BALANCE',
        depositFrequency: 'MONTHLY',
        prePenaltyApplicable: false,
      };
    } else {
      const p = this.product;
      this.form = {
        name:                                p.name,
        shortName:                           p.shortName,
        description:                         p.description,
        currencyCode:                        p.currencyCode,
        nominalAnnualInterestRate:           p.nominalAnnualInterestRate,
        compoundingPeriod:                   p.compoundingPeriod,
        postingPeriod:                       p.postingPeriod,
        calculationType:                     p.calculationType,
        depositFrequency:                    p.depositFrequency,
        mandatoryRecommendedDepositAmount:   p.mandatoryRecommendedDepositAmount,
        minDepositTerm:                      p.minDepositTerm,
        minDepositTermType:                  p.minDepositTermType,
        maxDepositTerm:                      p.maxDepositTerm,
        maxDepositTermType:                  p.maxDepositTermType,
        minDepositAmount:                    p.minDepositAmount,
        maxDepositAmount:                    p.maxDepositAmount,
        prePenaltyApplicable:                p.prePenaltyApplicable,
        prePenaltyInterest:                  p.prePenaltyInterest,
      };
    }
    this.editMode = true;
    this.saveError = '';
  }

  cancelEdit(): void {
    if (this.isNew) { this.router.navigate(['..'], { relativeTo: this.route }); return; }
    this.editMode = false;
    this.saveError = '';
  }

  save(): void {
    this.saving = true;
    this.saveError = '';
    const req$ = this.isNew
      ? this.svc.createRecurringDepositProduct(this.form)
      : this.svc.updateRecurringDepositProduct(this.product!.id, this.form);

    req$.subscribe({
      next: p => {
        this.product  = p;
        this.saving   = false;
        this.editMode = false;
        if (this.isNew) {
          this.isNew = false;
          this.router.navigate(['..', p.id], { relativeTo: this.route });
        }
      },
      error: () => { this.saveError = 'Save failed. Check all required fields.'; this.saving = false; },
    });
  }

  confirmDeactivate(): void { this.showDeactivateConfirm = true; }

  deactivate(): void {
    if (!this.product) return;
    this.deactivating = true;
    this.svc.deactivateRecurringDepositProduct(this.product.id).subscribe({
      next: () => { this.router.navigate(['..'], { relativeTo: this.route }); },
      error: () => { this.deactivating = false; this.showDeactivateConfirm = false; },
    });
  }

  setSection(s: DetailSection): void { this.activeSection = s; }

  label(map: Record<string, string>, key: string): string { return map[key] ?? key; }
}
