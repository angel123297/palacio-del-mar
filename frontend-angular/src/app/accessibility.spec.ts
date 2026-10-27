import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { AuthModalComponent } from './components/auth-modal/auth-modal.component';
import { BookingBarComponent } from './components/booking-bar/booking-bar.component';
import { FooterComponent } from './components/footer/footer.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { ProfileComponent } from './pages/profile/profile';
import { AuthService } from './services/auth.service';

describe('Universal Accessibility Audit (WCAG 2.2 AA Compliance)', () => {
  describe('App Root Landmarks & Bypass Blocks (WCAG 2.4.1)', () => {
    let fixture: ComponentFixture<App>;
    let compiled: HTMLElement;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [App],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;
    });

    it('debe contener un enlace "Saltar al contenido principal" accesible para teclado', () => {
      const skipLink = compiled.querySelector('.skip-link');
      expect(skipLink).toBeTruthy();
      expect(skipLink?.getAttribute('href')).toBe('#main-content');
      expect(skipLink?.textContent?.trim()).toContain('Saltar al contenido principal');
    });

    it('debe tener un landmark <main> con id="main-content" y tabindex="-1" para recibir foco tras el salto', () => {
      const mainLandmark = compiled.querySelector('main#main-content');
      expect(mainLandmark).toBeTruthy();
      expect(mainLandmark?.getAttribute('tabindex')).toBe('-1');
    });
  });

  describe('Auth Modal Accessibility (WCAG 4.1.2 & 2.1.2)', () => {
    let fixture: ComponentFixture<AuthModalComponent>;
    let component: AuthModalComponent;
    let compiled: HTMLElement;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [AuthModalComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      fixture = TestBed.createComponent(AuthModalComponent);
      component = fixture.componentInstance;
      component.isOpen = true;
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;
    });

    it('debe implementar role="dialog" y aria-modal="true" en el backdrop', () => {
      const dialog = compiled.querySelector('[role="dialog"]');
      expect(dialog).toBeTruthy();
      expect(dialog?.getAttribute('aria-modal')).toBe('true');
    });

    it('debe vincular los campos de inicio de sesión con etiquetas explicitas (for/id)', () => {
      component.mode = 'login';
      fixture.detectChanges();

      const emailInput = compiled.querySelector('input#auth-login-email');
      const emailLabel = compiled.querySelector('label[for="auth-login-email"]');
      expect(emailInput).toBeTruthy();
      expect(emailLabel).toBeTruthy();

      const passInput = compiled.querySelector('input#auth-login-password');
      const passLabel = compiled.querySelector('label[for="auth-login-password"]');
      expect(passInput).toBeTruthy();
      expect(passLabel).toBeTruthy();
    });

    it('el botón de cerrar debe tener aria-label y tamaño de toque mínimo accesible', () => {
      const closeBtn = compiled.querySelector('button[aria-label="Cerrar modal de autenticación"]');
      expect(closeBtn).toBeTruthy();
    });
  });

  describe('Booking Bar & Search Landmark (WCAG 1.3.1)', () => {
    let fixture: ComponentFixture<BookingBarComponent>;
    let compiled: HTMLElement;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [BookingBarComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      fixture = TestBed.createComponent(BookingBarComponent);
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;
    });

    it('debe identificar la barra de búsqueda con role="search" y aria-label descriptivo', () => {
      const searchBox = compiled.querySelector('#booking-bar[role="search"]');
      expect(searchBox).toBeTruthy();
      expect(searchBox?.getAttribute('aria-label')).toContain('Buscador de suites');
    });

    it('el botón de búsqueda debe tener aria-label accesible', () => {
      const submitBtn = compiled.querySelector('button[type="submit"]');
      expect(submitBtn).toBeTruthy();
      expect(submitBtn?.getAttribute('aria-label')).toContain('Buscar suites');
    });
  });

  describe('Footer Accessibility (WCAG 1.3.1 & 2.4.4)', () => {
    let fixture: ComponentFixture<FooterComponent>;
    let compiled: HTMLElement;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [FooterComponent],
        providers: [provideRouter([])]
      }).compileComponents();

      fixture = TestBed.createComponent(FooterComponent);
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;
    });

    it('el footer debe contar con role="contentinfo"', () => {
      const footer = compiled.querySelector('footer[role="contentinfo"]');
      expect(footer).toBeTruthy();
    });

    it('los enlaces externos como WhatsApp deben advertir apertura en nueva pestaña', () => {
      const waLink = compiled.querySelector('a[href*="wa.me"]');
      expect(waLink).toBeTruthy();
      expect(waLink?.getAttribute('rel')).toContain('noopener');
      expect(waLink?.getAttribute('aria-label')).toContain('nueva pestaña');
    });
  });

  describe('Navbar Accessibility & Role Consistency (WCAG 1.4.3 & 3.2.4)', () => {
    let fixture: ComponentFixture<NavbarComponent>;
    let compiled: HTMLElement;
    let authService: AuthService;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [NavbarComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      authService = TestBed.inject(AuthService);
      fixture = TestBed.createComponent(NavbarComponent);
    });

    it('debe rotular "Mis Reservas" para un huésped y "Panel Admin" para un administrador', () => {
      localStorage.setItem('user', JSON.stringify({ id: 'u1', name: 'Carlos', role: 'USER' }));
      authService.loadCachedUser();
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;

      const bookingsLink = compiled.querySelector('a[routerLink="/mis-reservas"]');
      expect(bookingsLink).toBeTruthy();
      expect(bookingsLink?.textContent?.trim()).toBe('Mis Reservas');

      localStorage.setItem('user', JSON.stringify({ id: 'u1', name: 'Carlos', role: 'ADMIN' }));
      authService.loadCachedUser();
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.querySelector('a[routerLink="/mis-reservas"]')).toBeNull();
      const adminLink = compiled.querySelector('a[routerLink="/admin"]');
      expect(adminLink).toBeTruthy();
      expect(adminLink?.textContent?.trim()).toBe('Panel Admin');

      localStorage.removeItem('user');
    });

    it('el botón de Salir debe tener aria-label accesible y contraste visible', () => {
      localStorage.setItem('user', JSON.stringify({ id: 'u2', name: 'Laura', role: 'GUEST' }));
      authService.loadCachedUser();
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;

      const logoutBtn = compiled.querySelector('button[aria-label="Cerrar sesión"]');
      expect(logoutBtn).toBeTruthy();
      expect(logoutBtn?.textContent?.trim()).toBe('Salir');

      localStorage.removeItem('user');
    });

    it('al presionar Pon tu alojamiento debe abrir el modal en modo registro y rol host, incluso tras cerrar y reabrir', () => {
      localStorage.removeItem('user');
      authService.loadCachedUser();
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;

      const hostBtn = compiled.querySelector('button[aria-label="Pon tu alojamiento"]') as HTMLButtonElement;
      expect(hostBtn).toBeTruthy();
      hostBtn.click();
      fixture.detectChanges();

      const modalComp = fixture.debugElement.children.find(c => c.componentInstance instanceof AuthModalComponent)?.componentInstance as AuthModalComponent;
      expect(modalComp).toBeTruthy();
      expect(modalComp.mode).toBe('register');
      expect(modalComp.registerRole).toBe('host');

      // Cerrar modal
      modalComp.close();
      fixture.detectChanges();
      expect(modalComp.isOpen).toBe(false);

      // Reabrir con "Pon tu alojamiento"
      hostBtn.click();
      fixture.detectChanges();
      expect(modalComp.isOpen).toBe(true);
      expect(modalComp.mode).toBe('register');
      expect(modalComp.registerRole).toBe('host');

      // Cambiar a iniciar sesión dentro del modal debe preservar el portal anfitrión
      modalComp.switchToLogin();
      fixture.detectChanges();
      expect(modalComp.mode).toBe('login');
      expect(modalComp.selectedPortal).toBe('host');

      // Cambiar de vuelta a registro debe preservar el rol anfitrión
      modalComp.switchToRegister();
      fixture.detectChanges();
      expect(modalComp.mode).toBe('register');
      expect(modalComp.registerRole).toBe('host');
    });
  });

  describe('Profile Accessibility (WCAG 4.1.2 & 1.3.1)', () => {
    let fixture: ComponentFixture<ProfileComponent>;
    let compiled: HTMLElement;
    let authService: AuthService;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [ProfileComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      authService = TestBed.inject(AuthService);
      localStorage.setItem('user', JSON.stringify({ id: 'u-vip', name: 'Viajero VIP', email: 'vip@palacio.co' }));
      authService.loadCachedUser();
      fixture = TestBed.createComponent(ProfileComponent);
      fixture.componentInstance.loading = false;
      fixture.detectChanges();
      compiled = fixture.nativeElement as HTMLElement;
    });

    afterEach(() => {
      localStorage.removeItem('user');
    });

    it('debe vincular los inputs de perfil con sus etiquetas mediante for/id explícitos', () => {
      const nameInput = compiled.querySelector('input#profile-name');
      const nameLabel = compiled.querySelector('label[for="profile-name"]');
      expect(nameInput).toBeTruthy();
      expect(nameLabel).toBeTruthy();

      const emailInput = compiled.querySelector('input#profile-email');
      const emailLabel = compiled.querySelector('label[for="profile-email"]');
      expect(emailInput).toBeTruthy();
      expect(emailLabel).toBeTruthy();
    });

    it('debe cargar inmediatamente los datos desde AuthService para evitar formularios vacíos', () => {
      const component = fixture.componentInstance;
      expect(component.profileForm.value.name).toBe('Viajero VIP');
      expect(component.profileForm.value.email).toBe('vip@palacio.co');
    });
  });
});
