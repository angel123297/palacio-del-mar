import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AuthModalComponent } from '../auth-modal/auth-modal.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, AuthModalComponent],
  template: `
    <header id="navbar">
      <nav class="main-nav" style="gap: 10px;">
        <a routerLink="/" class="logo" style="flex-shrink: 0;">
          <span style="font-family: 'Cormorant Garamond', serif; font-size: clamp(1.25rem, 4vw, 1.8rem); font-weight: 400; color: #e2c99b; letter-spacing: 1px; white-space: nowrap;">
            Palacio del Mar
          </span>
        </a>

        <div class="nav-links">
          <button class="nav-link-btn" (click)="scrollTo('#habitaciones')">Suites</button>
          <a routerLink="/calendario" class="nav-link-btn" style="text-decoration: none;">Calendario</a>
          <button class="nav-link-btn" (click)="scrollTo('#location')">Ubicación</button>
        </div>

        <div class="nav-right" style="flex-shrink: 0;">
          <ng-container *ngIf="auth.currentUser() as user; else notLogged">
            <div class="nav-user-menu" style="display: flex; align-items: center; gap: 8px; flex-wrap: nowrap;">
              <a routerLink="/mis-reservas" class="btn-outline nav-user-btn" style="padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                {{ ((user.role || '').toLowerCase() === 'host' || (user.role || '').toLowerCase() === 'anfitrion' || (user.role || '').toLowerCase() === 'admin') ? 'Mis Viajes' : 'Mis Reservas' }}
              </a>
              <a routerLink="/perfil" class="btn-outline nav-user-btn" style="padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Perfil
              </a>
              <a *ngIf="(user.role || '').toLowerCase() === 'host' || (user.role || '').toLowerCase() === 'anfitrion'" routerLink="/anfitrion" class="btn-outline" style="padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Anfitrión
              </a>
              <a *ngIf="(user.role || '').toLowerCase() === 'admin'" routerLink="/admin" class="btn-outline" style="padding: 6px 12px; text-decoration: none; white-space: nowrap;">
                Admin
              </a>
              <button class="btn-outline" (click)="logout()" style="padding: 6px 12px; background: rgba(231,76,60,0.08); border-color: rgba(231,76,60,0.4); color: #ff7675; cursor: pointer; white-space: nowrap;">
                Salir
              </button>
            </div>
          </ng-container>
          <ng-template #notLogged>
            <div style="display: flex; align-items: center; gap: 10px; flex-wrap: nowrap;">
              <button class="link-btn" (click)="openAuth('login')" style="background: none; border: none; color: #f5f0e8; cursor: pointer; font-size: 0.82rem; white-space: nowrap;">
                Iniciar sesión
              </button>
              <button class="btn-outline" (click)="openAuth('register')" style="padding: 6px 14px; cursor: pointer; white-space: nowrap;">
                Regístrate
              </button>
            </div>
          </ng-template>
        </div>
      </nav>
    </header>

    <app-auth-modal [isOpen]="authModalOpen" [initialMode]="authMode" (closeEvent)="authModalOpen = false"></app-auth-modal>
  `
})
export class NavbarComponent {
  authModalOpen = false;
  authMode: 'login' | 'register' = 'login';

  constructor(public auth: AuthService, private router: Router) {}

  openAuth(mode: 'login' | 'register'): void {
    this.authMode = mode;
    this.authModalOpen = true;
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
