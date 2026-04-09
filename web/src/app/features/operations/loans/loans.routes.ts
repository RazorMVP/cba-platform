import { Routes } from '@angular/router';
import { LoansListComponent } from './loans-list';
import { LoanDetailComponent } from './loan-detail/loan-detail';

export const LOANS_ROUTES: Routes = [
  { path: '', component: LoansListComponent },
  { path: ':id', component: LoanDetailComponent },
];
