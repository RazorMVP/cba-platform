import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './kpi-card.html',
  styleUrl: './kpi-card.scss',
})
export class KpiCardComponent {
  @Input() title = '';
  @Input() value: string | number = '';
  @Input() subtitle = '';
  @Input() trend: 'up' | 'down' | 'neutral' = 'neutral';
  @Input() trendValue = '';
  @Input() icon = '';
  @Input() color: 'primary' | 'success' | 'warning' | 'error' = 'primary';
}
