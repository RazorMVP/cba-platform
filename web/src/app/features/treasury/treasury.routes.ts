import { Routes } from '@angular/router';
import { TreasuryPlacementsComponent } from './placements';
import { TreasuryInterbankComponent } from './interbank';

export const TREASURY_ROUTES: Routes = [
  { path: '', redirectTo: 'placements', pathMatch: 'full' },
  { path: 'placements', component: TreasuryPlacementsComponent },
  { path: 'interbank', component: TreasuryInterbankComponent },
];
