import { Routes } from '@angular/router';
import { DepositProductsListComponent } from './deposit-products-list';
import { DepositProductDetailComponent } from './deposit-product-detail/deposit-product-detail';

export const DEPOSIT_PRODUCTS_ROUTES: Routes = [
  { path: '', component: DepositProductsListComponent },
  { path: ':id', component: DepositProductDetailComponent },
];
