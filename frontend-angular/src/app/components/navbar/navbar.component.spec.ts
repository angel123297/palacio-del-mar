import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NavbarComponent } from './navbar.component';
import { AuthService } from '../../services/auth.service';
import { CurrencyService } from '../../services/currency.service';
import { ActivatedRoute, Router, NavigationEnd } from '@angular/router';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let mockAuthService: any;
  let mockCurrencyService: any;
  let mockRouter: any;
  let routerEvents$: Subject<any>;
  let queryParams$: Subject<any>;

  beforeEach(async () => {
    routerEvents$ = new Subject<any>();
    queryParams$ = new Subject<any>();

    mockAuthService = {
      isLoggedIn: vi.fn().mockReturnValue(false),
      currentUser: signal(null),
      logout: vi.fn()
    };

    mockCurrencyService = {
      currentCurrency: signal('COP'),
      setCurrency: vi.fn()
    };

    mockRouter = {
      url: '/',
      parseUrl: vi.fn().mockReturnValue({ queryParams: {} }),
      events: routerEvents$.asObservable(),
      navigate: vi.fn().mockResolvedValue(true)
    };

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: CurrencyService, useValue: mockCurrencyService },
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: { queryParams: queryParams$.asObservable() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and clean up subscriptions on destroy', () => {
    expect(component).toBeTruthy();
    expect((component as any).subs.closed).toBe(false);

    fixture.destroy();
    expect((component as any).subs.closed).toBe(true);
  });

  it('should clear previous interval and timeout when scrollTo is called repeatedly', () => {
    vi.useFakeTimers();
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');

    component.scrollTo('#habitaciones');
    expect((component as any).scrollInterval).toBeTruthy();

    // Call again before finish
    component.scrollTo('#location');
    expect(clearIntervalSpy).toHaveBeenCalled();

    fixture.destroy();
    expect(clearIntervalSpy).toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('should return correct role flags and home links', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);

    mockAuthService.currentUser.set({ role: 'admin', name: 'Admin User' });
    expect(component.isAdmin()).toBe(true);
    expect(component.isCorporate()).toBe(true);
    expect(component.getHomeLink()).toBe('/admin');

    mockAuthService.currentUser.set({ role: 'host', name: 'Host User' });
    expect(component.isHost()).toBe(true);
    expect(component.isCorporate()).toBe(true);
    expect(component.getHomeLink()).toBe('/anfitrion');

    mockAuthService.currentUser.set({ role: 'guest', name: 'Guest User' });
    expect(component.isGuest()).toBe(true);
    expect(component.isCorporate()).toBe(false);
    expect(component.getHomeLink()).toBe('/');
  });
});
