import { Component, ViewChild, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { AuthModalComponent } from '../auth-modal/auth-modal.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, AuthModalComponent],
  template: `
    <header id="navbar" role="banner">
      <nav class="main-nav" aria-label="Navegación principal" style="gap: 10px; display: flex; align-items: center; justify-content: space-between; position: relative;">
        <a [routerLink]="getHomeLink()" class="logo" (click)="closeMobileMenu()" aria-label="Palacio del Mar - Inicio" style="flex-shrink: 0; min-height: 44px; display: inline-flex; align-items: center; text-decoration: none;">
          <span style="font-family: 'Cormorant Garamond', serif; font-size: clamp(1.25rem, 4vw, 1.8rem); font-weight: 400; color: #e2c99b; letter-spacing: 1px; white-space: nowrap;">
            Palacio del Mar
          </span>
        </a>

        <!-- Desktop Navigation Links -->
        <div class="nav-links" *ngIf="!isCorporate()">
          <button class="nav-link-btn" (click)="scrollTo('#habitaciones')" aria-label="Ir a sección de suites">Suites</button>
          <a routerLink="/calendario" class="nav-link-btn" style="text-decoration: none;" aria-label="Ver calendario de disponibilidad">Calendario</a>
          <button class="nav-link-btn" (click)="scrollTo('#location')" aria-label="Ir a sección de ubicación">Ubicación</button>
        </div>

        <div class="nav-links" *ngIf="isAdmin()">
          <span style="font-size: 0.75rem; letter-spacing: 1.5px; text-transform: uppercase; color: #c9a96e; border: 1px solid rgba(201,169,110,0.3); padding: 4px 12px; border-radius: 4px; background: rgba(201,169,110,0.08); font-weight: 600; display: inline-flex; align-items: center;">
            <svg style="width: 14px; height: 14px; margin-right: 6px;" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z"/></svg>
            Consola Central de Administración
          </span>
        </div>

        <div class="nav-links" *ngIf="isHost()">
          <span style="font-size: 0.75rem; letter-spacing: 1.5px; text-transform: uppercase; color: #c9a96e; border: 1px solid rgba(201,169,110,0.3); padding: 4px 12px; border-radius: 4px; background: rgba(201,169,110,0.08); font-weight: 600; display: inline-flex; align-items: center;">
            <svg style="width: 14px; height: 14px; margin-right: 6px;" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 3L2 12h3v8h6v-6h2v6h6v-8h3L12 3zm0 3.5l5 4.5v6.5h-2v-6H9v6H7v-6.5l5-4.5z"/></svg>
            Portal Operativo de Anfitrión
          </span>
        </div>

        <div class="nav-right" style="flex-shrink: 0; display: flex; align-items: center; gap: 8px;">
          <ng-container *ngIf="auth.currentUser() as user; else notLogged">
            <div class="nav-user-menu" style="display: flex; align-items: center; gap: 8px; flex-wrap: nowrap;">
              <a *ngIf="isGuest()" routerLink="/mis-reservas" class="btn-outline nav-user-btn desktop-only-btn" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Mis Reservas
              </a>
              <a *ngIf="isHost()" routerLink="/anfitrion" class="btn-outline desktop-only-btn" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap; background: rgba(226, 201, 155, 0.15); border-color: #e2c99b; color: #e2c99b; font-weight: 600;">
                Panel Anfitrión
              </a>
              <a *ngIf="isAdmin()" routerLink="/admin" class="btn-outline desktop-only-btn" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap; background: rgba(226, 201, 155, 0.15); border-color: #e2c99b; color: #e2c99b; font-weight: 600;">
                Panel Admin
              </a>
              <a routerLink="/perfil" class="btn-outline nav-user-btn desktop-only-btn" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Perfil
              </a>
              <button class="btn-outline desktop-only-btn" (click)="logout()" aria-label="Cerrar sesión" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 14px; background: rgba(231,76,60,0.18); border-color: rgba(231,76,60,0.6); color: #ff9999; font-weight: 600; cursor: pointer; white-space: nowrap;">
                Salir
              </button>
            </div>
          </ng-container>
          <ng-template #notLogged>
            <div style="display: flex; align-items: center; gap: 8px; flex-wrap: nowrap;">
              <button class="btn-outline desktop-only-btn" (click)="openAuth('register', 'host')" aria-label="Pon tu alojamiento" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; cursor: pointer; white-space: nowrap; font-size: 0.82rem; border-color: rgba(226, 201, 155, 0.4); color: #e2c99b;">
                <svg style="width: 14px; height: 14px; margin-right: 5px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 21h18M5 21V5a2 2 0 012-2h10a2 2 0 012 2v16M9 9h1m4 0h1m-6 4h1m4 0h1m-6 4h1m4 0h1"/></svg>
                Pon tu alojamiento
              </button>
              <button class="btn-outline desktop-only-btn" (click)="openAuth('login')" aria-label="Abrir inicio de sesión" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 14px; cursor: pointer; white-space: nowrap; font-size: 0.82rem;">
                Iniciar sesión
              </button>
              <button class="btn-outline desktop-only-btn" (click)="openAuth('register')" aria-label="Abrir registro de nueva cuenta" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 14px; cursor: pointer; white-space: nowrap; font-size: 0.82rem; background: rgba(226, 201, 155, 0.15); border-color: #e2c99b; color: #e2c99b; font-weight: 600;">
                Regístrate
              </button>
            </div>
          </ng-template>

          <!-- Mobile Hamburger Toggle Button -->
          <button type="button" class="mobile-menu-btn" (click)="toggleMobileMenu()" [attr.aria-expanded]="mobileMenuOpen" aria-label="Menú de navegación principal" style="min-width: 44px; min-height: 44px; display: none; align-items: center; justify-content: center; background: rgba(255,255,255,0.06); border: 1px solid rgba(226, 201, 155, 0.4); border-radius: 8px; color: #e2c99b; cursor: pointer; padding: 8px;">
            <svg *ngIf="!mobileMenuOpen" style="width: 24px; height: 24px;" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16"/></svg>
            <svg *ngIf="mobileMenuOpen" style="width: 24px; height: 24px;" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
        </div>
      </nav>

      <!-- Mobile Navigation Drawer -->
      <div class="mobile-nav-drawer" *ngIf="mobileMenuOpen" style="position: fixed; top: 68px; left: 0; right: 0; bottom: 0; background: rgba(13, 13, 13, 0.98); backdrop-filter: blur(12px); z-index: 999; padding: 1.5rem; display: flex; flex-direction: column; justify-content: space-between; border-top: 1px solid rgba(226, 201, 155, 0.2); overflow-y: auto;">
        <div style="display: flex; flex-direction: column; gap: 0.5rem;">
          <div *ngIf="!isCorporate()" style="display: flex; flex-direction: column; gap: 0.25rem;">
            <button class="nav-link-btn" (click)="scrollTo('#habitaciones'); closeMobileMenu()" style="text-align: left; font-size: 1.15rem; padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,0.08); color: #fff; background: none; border-top: none; border-left: none; border-right: none; cursor: pointer; width: 100%;">
              Suites
            </button>
            <a routerLink="/calendario" (click)="closeMobileMenu()" class="nav-link-btn" style="text-align: left; font-size: 1.15rem; padding: 12px 0; text-decoration: none; border-bottom: 1px solid rgba(255,255,255,0.08); color: #fff; display: block;">
              Calendario
            </a>
            <button class="nav-link-btn" (click)="scrollTo('#location'); closeMobileMenu()" style="text-align: left; font-size: 1.15rem; padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,0.08); color: #fff; background: none; border-top: none; border-left: none; border-right: none; cursor: pointer; width: 100%;">
              Ubicación
            </button>
          </div>

          <!-- Authenticated Links for Mobile -->
          <ng-container *ngIf="auth.currentUser() as user; else mobileNotLogged">
            <div style="display: flex; flex-direction: column; gap: 0.75rem; margin-top: 1rem;">
              <a *ngIf="isGuest()" routerLink="/mis-reservas" (click)="closeMobileMenu()" class="btn-primary" style="text-align: center; padding: 12px; text-decoration: none; min-height: 44px; display: flex; align-items: center; justify-content: center;">
                Mis Reservas
              </a>
              <a *ngIf="isHost()" routerLink="/anfitrion" (click)="closeMobileMenu()" class="btn-primary" style="text-align: center; padding: 12px; text-decoration: none; min-height: 44px; display: flex; align-items: center; justify-content: center;">
                Panel Anfitrión
              </a>
              <a *ngIf="isAdmin()" routerLink="/admin" (click)="closeMobileMenu()" class="btn-primary" style="text-align: center; padding: 12px; text-decoration: none; min-height: 44px; display: flex; align-items: center; justify-content: center;">
                Panel Admin
              </a>
              <a routerLink="/perfil" (click)="closeMobileMenu()" class="btn-outline" style="text-align: center; padding: 12px; text-decoration: none; min-height: 44px; display: flex; align-items: center; justify-content: center;">
                Mi Perfil
              </a>
              <button (click)="logout(); closeMobileMenu()" class="btn-outline" style="padding: 12px; color: #ff9999; border-color: rgba(231,76,60,0.6); min-height: 44px; cursor: pointer;">
                Cerrar Sesión
              </button>
            </div>
          </ng-container>

          <ng-template #mobileNotLogged>
            <div style="display: flex; flex-direction: column; gap: 0.75rem; margin-top: 1rem;">
              <button class="btn-outline" (click)="openAuth('register', 'host'); closeMobileMenu()" style="padding: 12px; color: #e2c99b; border-color: #e2c99b; min-height: 44px; font-size: 0.95rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px;">
                <svg style="width: 16px; height: 16px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 21h18M5 21V5a2 2 0 012-2h10a2 2 0 012 2v16M9 9h1m4 0h1m-6 4h1m4 0h1m-6 4h1m4 0h1"/></svg>
                Pon tu alojamiento
              </button>
              <button class="btn-outline" (click)="openAuth('login'); closeMobileMenu()" style="padding: 12px; min-height: 44px; font-size: 0.95rem; cursor: pointer;">
                Iniciar Sesión
              </button>
              <button class="btn-primary" (click)="openAuth('register'); closeMobileMenu()" style="padding: 12px; min-height: 44px; font-size: 0.95rem; cursor: pointer;">
                Regístrate
              </button>
            </div>
          </ng-template>
        </div>

        <div style="text-align: center; color: #666; font-size: 0.75rem; padding-top: 1rem; border-top: 1px solid rgba(255,255,255,0.06);">
          Palacio del Mar · Cartagena de Indias
        </div>
      </div>
    </header>

    <app-auth-modal #authModal [initialPortal]="authPortal" [initialMode]="authMode" [isOpen]="authModalOpen" (closeEvent)="onAuthClose()"></app-auth-modal>
  `
})
export class NavbarComponent implements OnInit {
  @ViewChild('authModal') authModal?: AuthModalComponent;

  authModalOpen = false;
  authMode: 'login' | 'register' = 'login';
  authPortal: 'guest' | 'host' | 'admin' = 'guest';
  mobileMenuOpen = false;

  constructor(
    public auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const handleParams = (params: any) => {
      const returnUrl = params['returnUrl'];
      const sessionExpired = params['sessionExpired'] === 'true';
      if ((returnUrl || sessionExpired) && !this.auth.isLoggedIn()) {
        setTimeout(() => {
          this.openAuth('login', 'guest');
          if (sessionExpired && this.authModal) {
            this.authModal.message = 'Tu sesión ha expirado por seguridad. Inicia sesión para continuar directamente donde estabas.';
            this.authModal.isSuccess = false;
          }
        }, 150);
      }
    };

    this.route.queryParams.subscribe(params => handleParams(params));

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      const tree = this.router.parseUrl(this.router.url);
      handleParams(tree.queryParams);
    });
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
  }

  @HostListener('window:resize')
  onResize(): void {
    if (window.innerWidth > 960 && this.mobileMenuOpen) {
      this.closeMobileMenu();
    }
  }

  onAuthClose(): void {
    this.authModalOpen = false;
    this.authPortal = 'guest';
    this.authMode = 'login';
  }

  isCorporate(): boolean {
    const role = (this.auth.currentUser()?.role || '').trim().toLowerCase();
    return role === 'host' || role === 'anfitrion' || role === 'admin';
  }

  isAdmin(): boolean {
    const role = (this.auth.currentUser()?.role || '').trim().toLowerCase();
    return role === 'admin';
  }

  isHost(): boolean {
    const role = (this.auth.currentUser()?.role || '').trim().toLowerCase();
    return role === 'host' || role === 'anfitrion';
  }

  isGuest(): boolean {
    return !this.isCorporate();
  }

  getHomeLink(): string {
    if (this.isAdmin()) return '/admin';
    if (this.isHost()) return '/anfitrion';
    return '/';
  }

  openAuth(mode: 'login' | 'register', portal: 'guest' | 'host' | 'admin' = 'guest'): void {
    this.authMode = mode;
    this.authPortal = portal;
    this.authModalOpen = true;
    if (this.authModal) {
      this.authModal.open(mode, portal);
    }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  scrollTo(hash: string): void {
    const rawPath = this.router.url.split('?')[0].split('#')[0];
    const targetId = hash.startsWith('#') ? hash.slice(1) : hash;

    const doScroll = () => {
      let attempts = 0;
      const interval = setInterval(() => {
        attempts++;
        const el = document.getElementById(targetId) || document.querySelector(hash);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth' });
          clearInterval(interval);
        } else if (attempts >= 15) {
          clearInterval(interval);
        }
      }, 60);
    };

    if (rawPath !== '/' && rawPath !== '') {
      this.router.navigate(['/'], { fragment: targetId }).then(() => {
        setTimeout(doScroll, 100);
      });
      return;
    }
    doScroll();
  }
}
