import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import Keycloak from 'keycloak-js';
import { TopbarComponent } from './topbar';
import { NotificationBellService } from '../notification-bell/notification-bell.service';

describe('TopbarComponent', () => {
  let keycloak: { tokenParsed?: Record<string, unknown>; logout: ReturnType<typeof vi.fn> };
  let bellSvc: Record<'getUnreadCount' | 'getInbox' | 'markAllRead', ReturnType<typeof vi.fn>>;

  function setup(tokenParsed?: Record<string, unknown>) {
    keycloak = { tokenParsed, logout: vi.fn() };
    bellSvc = {
      getUnreadCount: vi.fn().mockReturnValue(of({ count: 0 })),
      getInbox: vi.fn().mockReturnValue(of([])),
      markAllRead: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      imports: [TopbarComponent],
      providers: [
        { provide: Keycloak, useValue: keycloak },
        // TopbarComponent embeds <app-notification-bell>, which injects this service.
        { provide: NotificationBellService, useValue: bellSvc },
      ],
    });
    const fixture = TestBed.createComponent(TopbarComponent);
    fixture.detectChanges(); // renders the embedded notification bell too
    return fixture.componentInstance;
  }

  it('renders without error and shows the preferred_username', () => {
    const c = setup({ preferred_username: 'jdoe' });
    expect(c.username).toBe('jdoe');
  });

  it('falls back to "User" when no token is parsed', () => {
    const c = setup(undefined);
    expect(c.username).toBe('User');
  });

  it('logout delegates to keycloak.logout', () => {
    const c = setup({ preferred_username: 'jdoe' });
    c.logout();
    expect(keycloak.logout).toHaveBeenCalled();
  });
});
