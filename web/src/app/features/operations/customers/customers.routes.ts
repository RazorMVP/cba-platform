import { Routes } from '@angular/router';
import { CustomersListComponent } from './customers-list';
import { CustomerDetailComponent } from './customer-detail/customer-detail';

export const CUSTOMERS_ROUTES: Routes = [
  { path: '', component: CustomersListComponent },
  { path: ':id', component: CustomerDetailComponent },
];
