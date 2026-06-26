import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Keycloak from 'keycloak-js';
import { ShellComponent } from './shell';
import { NotificationBellService } from '../notification-bell/notification-bell.service';

describe('ShellComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]), // for <router-outlet>
        // Shell composes <app-topbar> (injects Keycloak) and
        // <app-notification-bell> (injects NotificationBellService).
        { provide: Keycloak, useValue: { tokenParsed: { preferred_username: 'admin' }, logout: vi.fn() } },
        {
          provide: NotificationBellService,
          useValue: {
            getUnreadCount: vi.fn().mockReturnValue(of({ count: 0 })),
            getInbox: vi.fn().mockReturnValue(of([])),
            markAllRead: vi.fn().mockReturnValue(of(undefined)),
          },
        },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges(); // renders sidebar + topbar + outlet
    return fixture.componentInstance;
  }

  it('renders the full shell (sidebar + topbar + outlet) without error', () => {
    const c = make();
    expect(c).toBeTruthy();
    expect(c.sidebarCollapsed).toBe(false);
  });

  it('toggleSidebar flips the collapsed flag', () => {
    const c = make();
    c.toggleSidebar();
    expect(c.sidebarCollapsed).toBe(true);
    c.toggleSidebar();
    expect(c.sidebarCollapsed).toBe(false);
  });
});
