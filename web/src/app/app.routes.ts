import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell';
import { isAuthenticated } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    canActivate: [isAuthenticated],
    children: [
      { path: '', redirectTo: 'operations/dashboard', pathMatch: 'full' },

      // ── Operations ─────────────────────────────────────────────────────────
      {
        path: 'operations',
        children: [
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
          {
            path: 'dashboard',
            loadComponent: () => import('./features/operations/dashboard/dashboard').then(m => m.DashboardComponent),
          },
          {
            path: 'customers',
            loadChildren: () => import('./features/operations/customers/customers.routes').then(m => m.CUSTOMERS_ROUTES),
          },
          {
            path: 'accounts',
            loadChildren: () => import('./features/operations/accounts/accounts.routes').then(m => m.ACCOUNTS_ROUTES),
          },
          {
            path: 'loans',
            loadChildren: () => import('./features/operations/loans/loans.routes').then(m => m.LOANS_ROUTES),
          },
          {
            path: 'payments',
            loadChildren: () => import('./features/operations/payments/payments.routes').then(m => m.PAYMENTS_ROUTES),
          },
          {
            path: 'teller',
            loadChildren: () => import('./features/operations/teller/teller.routes').then(m => m.TELLER_ROUTES),
          },
        ],
      },

      // ── Products ───────────────────────────────────────────────────────────
      {
        path: 'products',
        children: [
          { path: '', redirectTo: 'loan-products', pathMatch: 'full' },
          {
            path: 'loan-products',
            loadChildren: () => import('./features/products/loan-products/loan-products.routes').then(m => m.LOAN_PRODUCTS_ROUTES),
          },
          {
            path: 'deposit-products',
            loadChildren: () => import('./features/products/deposit-products/deposit-products.routes').then(m => m.DEPOSIT_PRODUCTS_ROUTES),
          },
          {
            path: 'fixed-deposits',
            loadChildren: () => import('./features/products/fixed-deposits/fixed-deposits.routes').then(m => m.FIXED_DEPOSITS_ROUTES),
          },
          {
            path: 'recurring-deposits',
            loadChildren: () => import('./features/products/recurring-deposits/recurring-deposits.routes').then(m => m.RECURRING_DEPOSITS_ROUTES),
          },
          {
            path: 'shares',
            loadChildren: () => import('./features/products/shares/shares.routes').then(m => m.SHARES_ROUTES),
          },
          {
            path: 'charges',
            loadChildren: () => import('./features/products/charges/charges.routes').then(m => m.CHARGES_ROUTES),
          },
        ],
      },

      // ── Groups & Centers ───────────────────────────────────────────────────
      {
        path: 'groups',
        loadChildren: () => import('./features/groups/groups.routes').then(m => m.GROUPS_ROUTES),
      },

      // ── Accounting ─────────────────────────────────────────────────────────
      {
        path: 'accounting',
        loadChildren: () => import('./features/accounting/accounting.routes').then(m => m.ACCOUNTING_ROUTES),
      },

      // ── Reports ────────────────────────────────────────────────────────────
      {
        path: 'reports',
        loadChildren: () => import('./features/reports/reports.routes').then(m => m.REPORTS_ROUTES),
      },

      // ── System Config ──────────────────────────────────────────────────────
      {
        path: 'system',
        loadChildren: () => import('./features/system/system.routes').then(m => m.SYSTEM_ROUTES),
      },

      // ── Admin ──────────────────────────────────────────────────────────────
      {
        path: 'admin',
        loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES),
      },

      // ── Open Banking ───────────────────────────────────────────────────────
      {
        path: 'open-banking',
        loadChildren: () => import('./features/open-banking/open-banking.routes').then(m => m.OPEN_BANKING_ROUTES),
      },

      // ── Treasury ───────────────────────────────────────────────────────────
      {
        path: 'treasury',
        loadChildren: () => import('./features/treasury/treasury.routes').then(m => m.TREASURY_ROUTES),
      },

      // ── Cards ──────────────────────────────────────────────────────────────
      {
        path: 'cards',
        loadChildren: () => import('./features/cards/cards.routes').then(m => m.CARDS_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: 'operations/dashboard' },
];
