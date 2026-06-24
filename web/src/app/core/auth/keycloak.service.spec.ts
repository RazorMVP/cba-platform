import { TestBed } from '@angular/core/testing';
import { CbaKeycloakService } from './keycloak.service';
import { environment } from '../../../environments/environment';

describe('CbaKeycloakService', () => {
  let service: CbaKeycloakService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [CbaKeycloakService] });
    service = TestBed.inject(CbaKeycloakService);
  });

  it('getRoles returns an empty array until populated at runtime', () => {
    expect(service.getRoles()).toEqual([]);
  });

  it('hasRole returns false when no roles are present', () => {
    expect(service.hasRole('ADMIN')).toBe(false);
  });

  it('getKeycloakUrl returns the configured environment URL', () => {
    expect(service.getKeycloakUrl()).toBe(environment.keycloak.url);
  });
});
