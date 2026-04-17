import { Routes } from '@angular/router';
import { GlAccountsComponent } from './gl-accounts';
import { JournalEntriesComponent } from './journal-entries';
import { ProvisioningComponent } from './provisioning';
import { FinancialActivityAccountsComponent } from './financial-activity-accounts';
import { GlClosuresComponent } from './gl-closures';

export const ACCOUNTING_ROUTES: Routes = [
  { path: 'gl-accounts', component: GlAccountsComponent },
  { path: 'journal-entries', component: JournalEntriesComponent },
  { path: 'provisioning', component: ProvisioningComponent },
  { path: 'financial-activity', component: FinancialActivityAccountsComponent },
  { path: 'gl-closures', component: GlClosuresComponent },
];
