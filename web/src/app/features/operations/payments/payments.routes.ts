import { Routes } from '@angular/router';
import { PaymentsListComponent } from './payments-list';
import { PaymentDetailComponent } from './payment-detail/payment-detail';

export const PAYMENTS_ROUTES: Routes = [
  { path: '', component: PaymentsListComponent },
  { path: ':id', component: PaymentDetailComponent },
];
