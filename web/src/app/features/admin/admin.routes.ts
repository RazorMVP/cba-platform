import { Routes } from '@angular/router';
import { UsersComponent } from './users';
import { RolesComponent } from './roles';
import { OfficesComponent } from './offices';
import { OpenBankingComponent } from './open-banking';
import { HooksComponent } from './hooks';
import { MakerCheckerComponent } from './maker-checker';
import { NotificationsComponent } from './notifications';
import { AuditLogComponent } from './audit-log';
import { SmsCampaignsComponent } from './sms-campaigns';
import { StaffComponent } from './staff';
import { StandingInstructionsComponent } from './standing-instructions';

export const ADMIN_ROUTES: Routes = [
  { path: 'users', component: UsersComponent },
  { path: 'roles', component: RolesComponent },
  { path: 'offices', component: OfficesComponent },
  { path: 'staff', component: StaffComponent },
  { path: 'open-banking', component: OpenBankingComponent },
  { path: 'hooks', component: HooksComponent },
  { path: 'maker-checker', component: MakerCheckerComponent },
  { path: 'notifications', component: NotificationsComponent },
  { path: 'audit-log', component: AuditLogComponent },
  { path: 'sms-campaigns', component: SmsCampaignsComponent },
  { path: 'standing-instructions', component: StandingInstructionsComponent },
];
