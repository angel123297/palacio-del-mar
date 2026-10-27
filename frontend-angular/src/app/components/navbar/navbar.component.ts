import { Component, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AuthModalComponent } from '../auth-modal/auth-modal.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, AuthModalComponent],
  template: `
    <header id="navbar" role="banner">
      <nav class="main-nav" aria-label="Navegación principal" style="gap: 10px;">
        <a [routerLink]="getHomeLink()" class="logo" aria-label="Palacio del Mar - Inicio" style="flex-shrink: 0; min-height: 44px; display: inline-flex; align-items: center;">
          <span style="font-family: 'Cormorant Garamond', serif; font-size: clamp(1.25rem, 4vw, 1.8rem); font-weight: 400; color: #e2c99b; letter-spacing: 1px; white-space: nowrap;">
            Palacio del Mar
          </span>
        </a>

        <div class="nav-links" *ngIf="!isCorporate()">
          <button class="nav-link-btn" (click)="scrollTo('#habitaciones')" aria-label="Ir a sección de suites">Suites</button>
          <a routerLink="/calendario" class="nav-link-btn" style="text-decoration: none;" aria-label="Ver calendario de disponibilidad">Calendario</a>
          <button class="nav-link-btn" (click)="scrollTo('#location')" aria-label="Ir a sección de ubicación">Ubicación</button>
        </div>

        <div class="nav-links" *ngIf="isAdmin()">
          <span style="font-size: 0.75rem; letter-spacing: 1.5px; text-transform: uppercase; color: #c9a96e; border: 1px solid rgba(201,169,110,0.3); padding: 4px 12px; border-radius: 4px; background: rgba(201,169,110,0.08); font-weight: 600;">
            🏢 Consola Central de Administración
          </span>
        </div>

        <div class="nav-links" *ngIf="isHost()">
          <span style="font-size: 0.75rem; letter-spacing: 1.5px; text-transform: uppercase; color: #c9a96e; border: 1px solid rgba(201,169,110,0.3); padding: 4px 12px; border-radius: 4px; background: rgba(201,169,110,0.08); font-weight: 600;">
            🏢 Portal Operativo de Anfitrión
          </span>
        </div>

        <div class="nav-right" style="flex-shrink: 0;">
          <ng-container *ngIf="auth.currentUser() as user; else notLogged">
            <div class="nav-user-menu" style="display: flex; align-items: center; gap: 8px; flex-wrap: nowrap;">
              <a *ngIf="isGuest()" routerLink="/mis-reservas" class="btn-outline nav-user-btn" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Mis Reservas
              </a>
              <a *ngIf="isHost()" routerLink="/anfitrion" class="btn-outline" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap; background: rgba(226, 201, 155, 0.15); border-color: #e2c99b; color: #e2c99b; font-weight: 600;">
                Panel Anfitrión
              </a>
              <a *ngIf="isAdmin()" routerLink="/admin" class="btn-outline" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap; background: rgba(226, 201, 155, 0.15); border-color: #e2c99b; color: #e2c99b; font-weight: 600;">
                Panel Admin
              </a>
              <a routerLink="/perfil" class="btn-outline nav-user-btn" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Perfil
              </a>
              <button class="btn-outline" (click)="logout()" aria-label="Cerrar sesión" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 14px; background: rgba(231,76,60,0.18); border-color: rgba(231,76,60,0.6); color: #ff9999; font-weight: 600; cursor: pointer; white-space: nowrap;">
                Salir
              </button>
            </div>
          </ng-container>
          <ng-template #notLogged>
            <div style="display: flex; align-items: center; gap: 8px; flex-wrap: nowrap;">
              <button class="btn-outline" (click)="openAuth('register', 'host')" aria-label="Pon tu alojamiento" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 12px; cursor: pointer; white-space: nowrap; font-size: 0.82rem; border-color: rgba(226, 201, 155, 0.4); color: #e2c99b;">
                🏨 Pon tu alojamiento
              </button>
              <button class="btn-outline" (click)="openAuth('login')" aria-label="Abrir inicio de sesión" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 14px; cursor: pointer; white-space: nowrap; font-size: 0.82rem;">
                Iniciar sesión
              </button>
              <button class="btn-outline" (click)="openAuth('register')" aria-label="Abrir registro de nueva cuenta" style="min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 6px 14px; cursor: pointer; white-space: nowrap; font-size: 0.82rem; background: rgba(226, 201, 155, 0.15); border-color: #e2c99b; color: #e2c99b; font-weight: 600;">
                Regístrate
              </button>
            </div>
          </ng-template>
        </div>
      </nav>
    </header>

    <app-auth-modal #authModal [initialPortal]="authPortal" [initialMode]="authMode" [isOpen]="authModalOpen" (closeEvent)="onAuthClose()"></app-auth-modal>
  `
})
export class NavbarComponent {
  @ViewChild('authModal') authModal?: AuthModalComponent;

  authModalOpen = false;
  authMode: 'login' | 'register' = 'login';
  authPortal: 'guest' | 'host' | 'admin' = 'guest';

  constructor(public auth: AuthService, private router: Router) {}

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
    if (this.router.url !== '/') {
      this.router.navigate(['/']).then(() => {
        setTimeout(() => {
          document.querySelector(hash)?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      });
      return;
    }
    document.querySelector(hash)?.scrollIntoView({ behavior: 'smooth' });
  }
}
