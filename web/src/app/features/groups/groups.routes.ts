import { Routes } from '@angular/router';
import { GroupsListComponent } from './groups-list';
import { CentersListComponent } from './centers-list';
import { GroupDetailComponent } from './group-detail/group-detail';
import { CenterDetailComponent } from './center-detail/center-detail';

export const GROUPS_ROUTES: Routes = [
  { path: '', component: GroupsListComponent },
  { path: 'centers', component: CentersListComponent },
  { path: 'groups/:id', component: GroupDetailComponent },
  { path: 'centers/:id', component: CenterDetailComponent },
];
