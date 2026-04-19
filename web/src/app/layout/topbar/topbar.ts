import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import Keycloak from 'keycloak-js';
import { NotificationBellComponent } from '../notification-bell/notification-bell';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule, NotificationBellComponent],
  templateUrl: './topbar.html',
  styleUrl: './topbar.scss',
})
export class TopbarComponent {
  private readonly keycloak = inject(Keycloak);

  get username(): string {
    return this.keycloak.tokenParsed?.['preferred_username'] ?? 'User';
  }

  logout(): void {
    this.keycloak.logout();
  }
}
