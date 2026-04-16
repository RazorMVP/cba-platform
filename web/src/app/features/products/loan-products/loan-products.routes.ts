import { Routes } from '@angular/router';
import { LoanProductsListComponent } from './loan-products-list';
import { LoanProductDetailComponent } from './loan-product-detail/loan-product-detail';

export const LOAN_PRODUCTS_ROUTES: Routes = [
  { path: '', component: LoanProductsListComponent },
  { path: ':id', component: LoanProductDetailComponent },
];
