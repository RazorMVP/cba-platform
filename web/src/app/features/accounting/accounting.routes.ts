import { Routes } from '@angular/router';
import { GlAccountsComponent } from './gl-accounts';
import { JournalEntriesComponent } from './journal-entries';
import { ProvisioningComponent } from './provisioning';

export const ACCOUNTING_ROUTES: Routes = [
  { path: 'gl-accounts', component: GlAccountsComponent },
  { path: 'journal-entries', component: JournalEntriesComponent },
  { path: 'provisioning', component: ProvisioningComponent },
];
