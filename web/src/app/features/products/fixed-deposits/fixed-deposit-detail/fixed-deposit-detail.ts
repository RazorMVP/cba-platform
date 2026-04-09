import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-fixed-deposit-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, PageHeaderComponent, StatusBadgeComponent],
  templateUrl: './fixed-deposit-detail.html',
  styleUrl: './fixed-deposit-detail.scss',
})
export class FixedDepositDetailComponent {}
