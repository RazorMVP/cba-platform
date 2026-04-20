import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  readonly currentYear = new Date().getFullYear();

  private readonly router = inject(Router);
  private readonly keycloak = inject(Keycloak);

  signIn(): void {
    if (environment.authBypass) {
      this.router.navigate(['/operations/dashboard']);
    } else {
      this.keycloak.login();
    }
  }
}
