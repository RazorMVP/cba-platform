import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Thin wrapper around keycloak-js for token management.
 * Full Keycloak-Angular integration is wired in app.config.ts via provideKeycloak().
 */
@Injectable({ providedIn: 'root' })
export class CbaKeycloakService {
  getRoles(): string[] {
    // Populated at runtime by keycloak-angular's token parsing
    return [];
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  getKeycloakUrl(): string {
    return environment.keycloak.url;
  }
}
