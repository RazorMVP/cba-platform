import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { ProductService, ShareProduct, ShareProductRequest } from '../../product.service';

type DetailSection = 'core' | 'shares' | 'lockin';

@Component({
  selector: 'app-share-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './share-detail.html',
  styleUrl: './share-detail.scss',
})
export class ShareDetailComponent implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc    = inject(ProductService);

  product: ShareProduct | null = null;
  loading = true;
  error   = '';
  isNew   = false;

  editMode  = false;
  saving    = false;
  saveError = '';
  form!: ShareProductRequest;

  showDeactivateConfirm = false;
  deactivating = false;

  activeSection: DetailSection = 'core';

  readonly periodTypes = ['DAYS', 'WEEKS', 'MONTHS', 'YEARS'];
  readonly periodTypeLabels: Record<string, string> = {
    DAYS: 'Days', WEEKS: 'Weeks', MONTHS: 'Months', YEARS: 'Years',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      this.enterEditMode(true);
      return;
    }
    this.svc.getShareProduct(id).subscribe({
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
        allowDividendsForInactive: false,
      };
    } else {
      const p = this.product;
      this.form = {
        name:                              p.name,
        shortName:                         p.shortName,
        description:                       p.description,
        currencyCode:                      p.currencyCode,
        totalShares:                       p.totalShares,
        unitPrice:                         p.unitPrice,
        nominalShares:                     p.nominalShares,
        minimumShares:                     p.minimumShares,
        maximumShares:                     p.maximumShares,
        minimumActivePeriodFrequency:      p.minimumActivePeriodFrequency,
        minimumActivePeriodFrequencyType:  p.minimumActivePeriodFrequencyType,
        lockInPeriodFrequency:             p.lockInPeriodFrequency,
        lockInPeriodFrequencyType:         p.lockInPeriodFrequencyType,
        allowDividendsForInactive:         p.allowDividendsForInactive,
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
      ? this.svc.createShareProduct(this.form)
      : this.svc.updateShareProduct(this.product!.id, this.form);

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
    this.svc.deactivateShareProduct(this.product.id).subscribe({
      next: () => { this.router.navigate(['..'], { relativeTo: this.route }); },
      error: () => { this.deactivating = false; this.showDeactivateConfirm = false; },
    });
  }

  setSection(s: DetailSection): void { this.activeSection = s; }

  label(map: Record<string, string>, key: string): string { return map[key] ?? key; }
}
