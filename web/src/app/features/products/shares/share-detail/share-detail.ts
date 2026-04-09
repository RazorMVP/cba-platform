import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-share-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, PageHeaderComponent, StatusBadgeComponent],
  templateUrl: './share-detail.html',
  styleUrl: './share-detail.scss',
})
export class ShareDetailComponent {}
