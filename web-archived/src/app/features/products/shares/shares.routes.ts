import { Routes } from '@angular/router';
import { SharesListComponent } from './shares-list';
import { ShareDetailComponent } from './share-detail/share-detail';

export const SHARES_ROUTES: Routes = [
  { path: '', component: SharesListComponent },
  { path: ':id', component: ShareDetailComponent },
];
