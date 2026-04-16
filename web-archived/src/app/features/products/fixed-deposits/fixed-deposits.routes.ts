import { Routes } from '@angular/router';
import { FixedDepositsListComponent } from './fixed-deposits-list';
import { FixedDepositDetailComponent } from './fixed-deposit-detail/fixed-deposit-detail';

export const FIXED_DEPOSITS_ROUTES: Routes = [
  { path: '', component: FixedDepositsListComponent },
  { path: ':id', component: FixedDepositDetailComponent },
];
