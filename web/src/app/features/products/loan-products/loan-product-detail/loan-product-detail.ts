import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import {
  ProductService, LoanProduct, LoanProductCreateRequest,
  AllowAttributeOverrides,
} from '../../product.service';

type DetailSection = 'principal' | 'interest' | 'schedule' | 'grace' | 'accounting';

const DEFAULT_OVERRIDES: AllowAttributeOverrides = {
  amortizationType: true, interestType: true, repaymentEvery: true,
  repaymentFrequency: true, repaymentStrategy: true,
  graceOnPrincipalAndInterestPayment: true, graceOnInterestCharged: true,
  interestRatePerPeriod: true,
};

@Component({
  selector: 'app-loan-product-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './loan-product-detail.html',
  styleUrl: './loan-product-detail.scss',
})
export class LoanProductDetailComponent implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc    = inject(ProductService);

  product: LoanProduct | null = null;
  loading = true;
  error = '';
  isNew = false;

  // View/edit toggle
  editMode = false;
  saving = false;
  saveError = '';
  form!: LoanProductCreateRequest;

  // Confirm deactivate
  showDeactivateConfirm = false;
  deactivating = false;

  // Active section (accordion in edit mode)
  activeSection: DetailSection = 'principal';

  readonly interestRateFrequencyTypes = ['PER_YEAR', 'PER_MONTH', 'PER_WEEK', 'WHOLE_TERM'];
  readonly interestTypes              = ['DECLINING_BALANCE', 'FLAT'];
  readonly amortizationTypes          = ['EQUAL_INSTALLMENTS', 'EQUAL_PRINCIPAL'];
  readonly calcPeriodTypes            = ['SAME_AS_REPAYMENT_PERIOD', 'DAILY'];
  readonly daysInYearTypes            = ['ACTUAL', 'DAYS_360', 'DAYS_364', 'DAYS_365'];
  readonly daysInMonthTypes           = ['ACTUAL', 'DAYS_30'];
  readonly repaymentFreqTypes         = ['MONTHS', 'WEEKS', 'DAYS', 'YEARS'];
  readonly repaymentTypes             = ['ANNUITY', 'FLAT', 'DECLINING_BALANCE'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      this.enterEditMode(true);
      return;
    }
    this.svc.getLoanProduct(id).subscribe({
      next:  p  => { this.product = p; this.loading = false; },
      error: () => { this.error = 'Product not found.'; this.loading = false; },
    });
  }

  enterEditMode(blank = false): void {
    if (blank || !this.product) {
      this.form = {
        name: '', shortName: '', currencyCode: 'USD',
        minPrincipal: 0, maxPrincipal: 0, defaultPrincipal: undefined,
        minInterestRate: 0, maxInterestRate: 0, defaultInterestRate: 0,
        interestRateFrequencyType: 'PER_YEAR',
        interestType: 'DECLINING_BALANCE', amortizationType: 'EQUAL_INSTALLMENTS',
        interestCalculationPeriodType: 'SAME_AS_REPAYMENT_PERIOD',
        daysInYearType: 'ACTUAL', daysInMonthType: 'ACTUAL',
        minTermMonths: 1, maxTermMonths: 60,
        numberOfRepayments: 12, repaymentEvery: 1,
        repaymentFrequencyType: 'MONTHS', repaymentType: 'ANNUITY',
        originationFee: 0, latePaymentFee: 0,
        allowAttributeOverrides: { ...DEFAULT_OVERRIDES },
        chargeIds: [],
      };
    } else {
      const p = this.product;
      this.form = {
        name: p.name, shortName: p.shortName, description: p.description,
        currencyCode: p.currencyCode, fundId: p.fund?.id,
        minPrincipal: p.minPrincipal, maxPrincipal: p.maxPrincipal,
        defaultPrincipal: p.defaultPrincipal,
        installmentAmountInMultiplesOf: p.installmentAmountInMultiplesOf,
        minInterestRate: p.minInterestRate, maxInterestRate: p.maxInterestRate,
        defaultInterestRate: p.defaultInterestRate,
        interestRateFrequencyType: p.interestRateFrequencyType,
        interestType: p.interestType, amortizationType: p.amortizationType,
        interestCalculationPeriodType: p.interestCalculationPeriodType,
        daysInYearType: p.daysInYearType, daysInMonthType: p.daysInMonthType,
        minTermMonths: p.minTermMonths, maxTermMonths: p.maxTermMonths,
        numberOfRepayments: p.numberOfRepayments, repaymentEvery: p.repaymentEvery,
        repaymentFrequencyType: p.repaymentFrequencyType, repaymentType: p.repaymentType,
        graceOnPrincipalPayment: p.graceOnPrincipalPayment,
        graceOnInterestPayment: p.graceOnInterestPayment,
        graceOnInterestCharged: p.graceOnInterestCharged,
        graceOnArrearsAgeing: p.graceOnArrearsAgeing,
        inArrearsTolerance: p.inArrearsTolerance,
        originationFee: p.originationFee, latePaymentFee: p.latePaymentFee,
        allowAttributeOverrides: { ...(p.allowAttributeOverrides ?? DEFAULT_OVERRIDES) },
        fundSourceAccountId:             p.fundSourceAccount?.id,
        loanPortfolioAccountId:          p.loanPortfolioAccount?.id,
        transfersInSuspenseAccountId:    p.transfersInSuspenseAccount?.id,
        interestOnLoanAccountId:         p.interestOnLoanAccount?.id,
        incomeFromFeesAccountId:         p.incomeFromFeesAccount?.id,
        incomeFromPenaltiesAccountId:    p.incomeFromPenaltiesAccount?.id,
        writeOffAccountId:               p.writeOffAccount?.id,
        overpaymentLiabilityAccountId:   p.overpaymentLiabilityAccount?.id,
        chargeIds: p.charges.map(c => c.id),
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
      ? this.svc.createLoanProduct(this.form)
      : this.svc.updateLoanProduct(this.product!.id, this.form);

    req$.subscribe({
      next: p => {
        this.product = p;
        this.saving = false;
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
    this.svc.deactivateLoanProduct(this.product.id).subscribe({
      next: () => { this.router.navigate(['..'], { relativeTo: this.route }); },
      error: () => { this.deactivating = false; this.showDeactivateConfirm = false; },
    });
  }

  setSection(s: DetailSection): void { this.activeSection = s; }

  // Display helpers
  label(map: Record<string, string>, key: string): string { return map[key] ?? key; }

  readonly repaymentTypeLabels: Record<string, string> = {
    ANNUITY: 'Annuity (EMI)', FLAT: 'Flat', DECLINING_BALANCE: 'Declining Balance',
  };
  readonly interestTypeLabels: Record<string, string> = {
    DECLINING_BALANCE: 'Declining Balance', FLAT: 'Flat',
  };
  readonly amortizationLabels: Record<string, string> = {
    EQUAL_INSTALLMENTS: 'Equal Installments', EQUAL_PRINCIPAL: 'Equal Principal',
  };
  readonly freqLabels: Record<string, string> = {
    PER_YEAR: 'Per Year', PER_MONTH: 'Per Month', PER_WEEK: 'Per Week', WHOLE_TERM: 'Whole Term',
  };
  readonly repaymentFreqLabels: Record<string, string> = {
    MONTHS: 'Months', WEEKS: 'Weeks', DAYS: 'Days', YEARS: 'Years',
  };
  readonly calcPeriodLabels: Record<string, string> = {
    SAME_AS_REPAYMENT_PERIOD: 'Same as Repayment', DAILY: 'Daily',
  };
}
