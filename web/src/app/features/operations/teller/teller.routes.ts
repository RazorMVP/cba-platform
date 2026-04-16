import { Routes } from '@angular/router';
import { TellerListComponent } from './teller-list';
import { TellerDetailComponent } from './teller-detail/teller-detail';

export const TELLER_ROUTES: Routes = [
  { path: '', component: TellerListComponent },
  { path: ':id', component: TellerDetailComponent },
];
