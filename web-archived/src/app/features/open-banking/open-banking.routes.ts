import { Routes } from '@angular/router';
import { OpenBankingListComponent } from './open-banking-list';
import { ConsentDetailComponent } from './consent-detail/consent-detail';

export const OPEN_BANKING_ROUTES: Routes = [
  { path: '', component: OpenBankingListComponent },
  { path: ':id', component: ConsentDetailComponent },
];
