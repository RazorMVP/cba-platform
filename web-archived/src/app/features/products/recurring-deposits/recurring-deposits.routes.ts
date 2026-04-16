import { Routes } from '@angular/router';
import { RecurringDepositsListComponent } from './recurring-deposits-list';
import { RecurringDepositDetailComponent } from './recurring-deposit-detail/recurring-deposit-detail';

export const RECURRING_DEPOSITS_ROUTES: Routes = [
  { path: '', component: RecurringDepositsListComponent },
  { path: ':id', component: RecurringDepositDetailComponent },
];
