import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { KpiCardComponent } from '../../../shared/components/kpi-card/kpi-card';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, KpiCardComponent, PageHeaderComponent, StatusBadgeComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent {
  readonly kpis = [
    { title: 'Active Customers',   value: '—', icon: 'people',           color: 'primary' as const, trend: 'neutral' as const },
    { title: 'Active Loans',       value: '—', icon: 'payments',         color: 'success' as const, trend: 'neutral' as const },
    { title: 'Total Deposits',     value: '—', icon: 'account_balance',  color: 'primary' as const, trend: 'neutral' as const },
    { title: 'Loans in Arrears',   value: '—', icon: 'warning',          color: 'warning' as const, trend: 'neutral' as const },
  ];
}
