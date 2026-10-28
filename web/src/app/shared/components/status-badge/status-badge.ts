import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `<span class="badge" [class]="'badge--' + variant">{{ label }}</span>`,
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './status-badge.scss',
})
export class StatusBadgeComponent {
  @Input() label = '';
  @Input() variant: BadgeVariant = 'neutral';
}
