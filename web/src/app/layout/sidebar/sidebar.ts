import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface NavItem {
  label: string;
  icon: string;
  route?: string;
  exact?: boolean;
  children?: NavItem[];
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class SidebarComponent {
  @Input() collapsed = false;
  @Output() sidebarToggle = new EventEmitter<void>();

  expandedGroups: Set<string> = new Set(['Operations']);

  readonly navGroups: NavGroup[] = [
    {
      label: 'Operations',
      items: [
        { label: 'Dashboard',  icon: 'dashboard',         route: '/operations/dashboard', exact: true },
        { label: 'Customers',  icon: 'people',            route: '/operations/customers' },
        { label: 'Accounts',   icon: 'account_balance',   route: '/operations/accounts' },
        { label: 'Loans',      icon: 'payments',          route: '/operations/loans' },
        { label: 'Payments',   icon: 'swap_horiz',        route: '/operations/payments' },
        { label: 'Teller',     icon: 'point_of_sale',     route: '/operations/teller' },
      ],
    },
    {
      label: 'Products',
      items: [
        { label: 'Loan Products',      icon: 'credit_score',      route: '/products/loan-products' },
        { label: 'Deposit Products',   icon: 'savings',           route: '/products/deposit-products' },
        { label: 'Fixed Deposits',     icon: 'lock_clock',        route: '/products/fixed-deposits' },
        { label: 'Recurring Deposits', icon: 'autorenew',         route: '/products/recurring-deposits' },
        { label: 'Share Products',     icon: 'pie_chart',         route: '/products/shares' },
        { label: 'Charges',            icon: 'percent',           route: '/products/charges' },
      ],
    },
    {
      label: 'Groups',
      items: [
        { label: 'Groups & Centers',   icon: 'groups',            route: '/groups' },
      ],
    },
    {
      label: 'Accounting',
      items: [
        { label: 'GL Accounts',        icon: 'menu_book',         route: '/accounting/gl-accounts' },
        { label: 'Journal Entries',    icon: 'receipt_long',      route: '/accounting/journal-entries' },
        { label: 'Provisioning',       icon: 'shield',            route: '/accounting/provisioning' },
        { label: 'Financial Activity', icon: 'account_tree',      route: '/accounting/financial-activity' },
        { label: 'GL Closures',        icon: 'lock_clock',        route: '/accounting/gl-closures' },
        { label: 'Accounting Rules',   icon: 'rule',              route: '/accounting/accounting-rules' },
      ],
    },
    {
      label: 'Reports',
      items: [
        { label: 'Reports',            icon: 'bar_chart',         route: '/reports/list' },
        { label: 'CoB Scheduler',      icon: 'schedule',          route: '/reports/cob' },
        { label: 'Mailing Jobs',       icon: 'email',             route: '/reports/mailing' },
      ],
    },
    {
      label: 'System',
      items: [
        { label: 'Codes & Values',     icon: 'list_alt',          route: '/system/codes' },
        { label: 'Global Config',      icon: 'tune',              route: '/system/config' },
        { label: 'Floating Rates',     icon: 'trending_up',       route: '/system/floating-rates' },
        { label: 'Taxes',              icon: 'account_balance_wallet', route: '/system/taxes' },
        { label: 'Account Algorithms', icon: 'pin',               route: '/system/account-algorithms' },
        { label: 'Holidays',           icon: 'event_busy',        route: '/system/holidays' },
        { label: 'Payment Types',      icon: 'payments',          route: '/system/payment-types' },
        { label: 'Exchange Rates',     icon: 'currency_exchange', route: '/system/exchange-rates' },
        { label: 'Funds',              icon: 'savings',           route: '/system/funds' },
        { label: 'Acct No. Formats',   icon: 'pin',               route: '/system/account-number-formats' },
        { label: 'DataTables',         icon: 'table_chart',       route: '/system/datatables' },
        { label: 'Surveys',            icon: 'quiz',              route: '/system/surveys' },
        { label: 'Credit Bureau',       icon: 'verified_user',     route: '/system/credit-bureau' },
        { label: 'Field Configuration', icon: 'tune',              route: '/system/field-configuration' },
      ],
    },
    {
      label: 'Cards',
      items: [
        { label: 'Card List',          icon: 'credit_card',         route: '/cards',             exact: true },
        { label: 'Card Products',      icon: 'style',               route: '/cards/products' },
        { label: 'Fraud Rules',        icon: 'security',            route: '/cards/fraud' },
        { label: 'Settlement',         icon: 'receipt',             route: '/cards/settlement' },
        { label: 'Disputes',           icon: 'gavel',               route: '/cards/disputes' },
        { label: 'Terminal Simulator', icon: 'point_of_sale',       route: '/cards/terminal' },
        { label: 'BIN Management',     icon: 'dialpad',             route: '/cards/bins' },
        { label: 'Schemes',            icon: 'hub',                 route: '/cards/schemes' },
        { label: 'Interchange',        icon: 'currency_exchange',   route: '/cards/interchange' },
        { label: 'API Keys',           icon: 'key',                 route: '/cards/api-keys' },
        { label: 'Webhooks',           icon: 'webhook',             route: '/cards/webhooks' },
      ],
    },
    {
      label: 'Treasury',
      items: [
        { label: 'Placements',         icon: 'account_balance',   route: '/treasury/placements' },
        { label: 'Interbank',          icon: 'swap_horiz',        route: '/treasury/interbank' },
        { label: 'Liquidity',          icon: 'water_drop',        route: '/treasury/liquidity' },
      ],
    },
    {
      label: 'Admin',
      items: [
        { label: 'Users',              icon: 'manage_accounts',   route: '/admin/users' },
        { label: 'Roles',              icon: 'admin_panel_settings', route: '/admin/roles' },
        { label: 'Offices & Staff',    icon: 'corporate_fare',    route: '/admin/offices' },
        { label: 'Staff',              icon: 'badge',             route: '/admin/staff' },
        { label: 'Open Banking',       icon: 'open_in_new',       route: '/admin/open-banking' },
        { label: 'Hooks',              icon: 'webhook',           route: '/admin/hooks' },
        { label: 'Maker-Checker',      icon: 'verified',          route: '/admin/maker-checker' },
        { label: 'Notifications',      icon: 'notifications',     route: '/admin/notifications' },
        { label: 'Audit Log',          icon: 'manage_search',     route: '/admin/audit-log' },
        { label: 'SMS Campaigns',      icon: 'sms',               route: '/admin/sms-campaigns' },
        { label: 'Standing Instructions', icon: 'repeat',         route: '/admin/standing-instructions' },
        { label: 'Login History',         icon: 'login',             route: '/admin/login-history' },
        { label: 'Compliance Reports',    icon: 'policy',            route: '/admin/compliance' },
        { label: 'Bulk Import',           icon: 'upload_file',       route: '/admin/bulk-import' },
        { label: 'Security Policy',       icon: 'security',          route: '/admin/security-policy' },
      ],
    },
  ];

  toggleGroup(label: string): void {
    if (this.expandedGroups.has(label)) {
      this.expandedGroups.delete(label);
    } else {
      this.expandedGroups.add(label);
    }
  }

  isGroupExpanded(label: string): boolean {
    return this.expandedGroups.has(label);
  }
}
