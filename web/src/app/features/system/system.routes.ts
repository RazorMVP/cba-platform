import { Routes } from '@angular/router';
import { CodesComponent } from './codes';
import { GlobalConfigComponent } from './global-config';
import { FloatingRatesComponent } from './floating-rates';
import { TaxesComponent } from './taxes';
import { AccountAlgorithmsComponent } from './account-algorithms';

export const SYSTEM_ROUTES: Routes = [
  { path: 'codes', component: CodesComponent },
  { path: 'config', component: GlobalConfigComponent },
  { path: 'floating-rates', component: FloatingRatesComponent },
  { path: 'taxes', component: TaxesComponent },
  { path: 'account-algorithms', component: AccountAlgorithmsComponent },
];
