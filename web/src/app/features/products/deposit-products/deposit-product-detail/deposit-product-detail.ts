import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { ProductService, DepositProduct, DepositProductCreateRequest } from '../../product.service';

type DetailSection = 'core' | 'interest' | 'lockin' | 'overdraft' | 'accounting';

@Component({
  selector: 'app-deposit-product-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './deposit-product-detail.html',
  styleUrl: './deposit-product-detail.scss',
})
export class DepositProductDetailComponent implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc    = inject(ProductService);

  product: DepositProduct | null = null;
  loading = true;
  error = '';
  isNew = false;

  editMode  = false;
  saving    = false;
  saveError = '';
  form!: DepositProductCreateRequest;

  showDeactivateConfirm = false;
  deactivating = false;

  activeSection: DetailSection = 'core';

  readonly accountTypes          = ['SAVINGS', 'CHECKING', 'FIXED_DEPOSIT'];
  readonly interestCompoundings  = ['DAILY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'];
  readonly interestPostingTypes  = ['MONTHLY', 'QUARTERLY', 'ANNUALLY', 'SEMI_ANNUAL'];
  readonly daysInYearTypes       = ['ACTUAL', 'DAYS_360', 'DAYS_364', 'DAYS_365'];
  readonly daysInMonthTypes      = ['ACTUAL', 'DAYS_30'];
  readonly lockinFreqTypes       = ['MONTHS', 'WEEKS', 'DAYS', 'YEARS'];
  readonly accountingTypes       = ['NONE', 'CASH'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      this.enterEditMode(true);
      return;
    }
    this.svc.getDepositProduct(id).subscribe({
      next:  p  => { this.product = p; this.loading = false; },
      error: () => { this.error = 'Product not found.'; this.loading = false; },
    });
  }

  enterEditMode(blank = false): void {
    if (blank || !this.product) {
      this.form = {
        name: '', shortName: '', accountType: 'SAVINGS', currencyCode: 'USD',
        minimumBalance: 0, interestRate: 0,
        interestCompounding: 'MONTHLY', interestPostingPeriodType: 'MONTHLY',
        daysInYearType: 'ACTUAL', daysInMonthType: 'ACTUAL',
        withdrawalFeeForTransfers: false, allowOverdraft: false,
        accountingType: 'NONE', chargeIds: [],
      };
    } else {
      const p = this.product;
      this.form = {
        name: p.name, shortName: p.shortName, description: p.description,
        accountType: p.accountType, currencyCode: p.currencyCode,
        minimumBalance: p.minimumBalance,
        minRequiredOpeningBalance: p.minRequiredOpeningBalance,
        interestRate: p.interestRate,
        interestCompounding: p.interestCompounding,
        interestPostingPeriodType: p.interestPostingPeriodType,
        daysInYearType: p.daysInYearType, daysInMonthType: p.daysInMonthType,
        lockinPeriodFrequency: p.lockinPeriodFrequency,
        lockinPeriodFrequencyType: p.lockinPeriodFrequencyType,
        withdrawalFeeForTransfers: p.withdrawalFeeForTransfers,
        allowOverdraft: p.allowOverdraft,
        overdraftLimit: p.overdraftLimit,
        nominalAnnualInterestRateOverdraft: p.nominalAnnualInterestRateOverdraft,
        minOverdraftForInterestCalculation: p.minOverdraftForInterestCalculation,
        accountingType: p.accountingType,
        savingsReferenceAccountId:          p.savingsReferenceAccount?.id,
        savingsControlAccountId:            p.savingsControlAccount?.id,
        transfersInSuspenseAccountId:       p.transfersInSuspenseAccount?.id,
        interestOnSavingsAccountId:         p.interestOnSavingsAccount?.id,
        incomeFromFeesAccountId:            p.incomeFromFeesAccount?.id,
        incomeFromPenaltiesAccountId:       p.incomeFromPenaltiesAccount?.id,
        writeOffAccountId:                  p.writeOffAccount?.id,
        overdraftPortfolioControlAccountId: p.overdraftPortfolioControlAccount?.id,
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
      ? this.svc.createDepositProduct(this.form)
      : this.svc.updateDepositProduct(this.product!.id, this.form);

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
    this.svc.deactivateDepositProduct(this.product.id).subscribe({
      next: () => { this.router.navigate(['..'], { relativeTo: this.route }); },
      error: () => { this.deactivating = false; this.showDeactivateConfirm = false; },
    });
  }

  setSection(s: DetailSection): void { this.activeSection = s; }

  label(map: Record<string, string>, key: string): string { return map[key] ?? key; }

  readonly accountTypeLabels: Record<string, string> = {
    SAVINGS: 'Savings', CHECKING: 'Checking', FIXED_DEPOSIT: 'Fixed Deposit',
  };
  readonly compoundingLabels: Record<string, string> = {
    DAILY: 'Daily', MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', ANNUALLY: 'Annually',
  };
  readonly postingPeriodLabels: Record<string, string> = {
    MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', ANNUALLY: 'Annually', SEMI_ANNUAL: 'Semi-Annual',
  };
  readonly lockinFreqLabels: Record<string, string> = {
    MONTHS: 'Months', WEEKS: 'Weeks', DAYS: 'Days', YEARS: 'Years',
  };

  readonly glAccountFields: Array<{ key: keyof DepositProduct; label: string }> = [
    { key: 'savingsReferenceAccount',          label: 'Savings Reference'          },
    { key: 'savingsControlAccount',            label: 'Savings Control'            },
    { key: 'transfersInSuspenseAccount',       label: 'Transfers in Suspense'      },
    { key: 'interestOnSavingsAccount',         label: 'Interest on Savings'        },
    { key: 'incomeFromFeesAccount',            label: 'Income from Fees'           },
    { key: 'incomeFromPenaltiesAccount',       label: 'Income from Penalties'      },
    { key: 'writeOffAccount',                  label: 'Write-Off'                  },
    { key: 'overdraftPortfolioControlAccount', label: 'Overdraft Portfolio Control'},
  ];
}
