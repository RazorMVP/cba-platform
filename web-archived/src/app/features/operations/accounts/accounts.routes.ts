import { Routes } from '@angular/router';
import { AccountsListComponent } from './accounts-list';
import { AccountDetailComponent } from './account-detail/account-detail';

export const ACCOUNTS_ROUTES: Routes = [
  { path: '', component: AccountsListComponent },
  { path: ':id', component: AccountDetailComponent },
];
