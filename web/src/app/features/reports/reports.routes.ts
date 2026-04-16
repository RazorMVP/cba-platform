import { Routes } from '@angular/router';
import { ReportsListComponent } from './reports-list';
import { CobSchedulerComponent } from './cob-scheduler';
import { ReportMailingComponent } from './report-mailing';

export const REPORTS_ROUTES: Routes = [
  { path: 'list', component: ReportsListComponent },
  { path: 'cob', component: CobSchedulerComponent },
  { path: 'mailing', component: ReportMailingComponent },
];
