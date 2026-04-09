import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-fixed-deposits-list',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, PageHeaderComponent, StatusBadgeComponent],
  templateUrl: './fixed-deposits-list.html',
  styleUrl: './fixed-deposits-list.scss',
})
export class FixedDepositsListComponent {}
