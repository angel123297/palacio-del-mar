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
      <nav class="main-nav">
        <a routerLink="/" class="logo">
          <span style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; font-weight: 400; color: #e2c99b; letter-spacing: 1px;">
            Palacio del Mar
          </span>
        </a>

        <div class="nav-links">
          <button class="nav-link-btn" (click)="scrollTo('#rooms')">Suites</button>
          <button class="nav-link-btn" (click)="scrollTo('#experiences')">Experiencias</button>
          <button class="nav-link-btn" (click)="scrollTo('#dining')">Gastronomía</button>
          <button class="nav-link-btn" (click)="scrollTo('#spa')">Spa</button>
          <button class="nav-link-btn" (click)="scrollTo('#location')">Ubicación</button>
        </div>

        <div class="nav-right">
          <ng-container *ngIf="auth.currentUser$ | async as user; else notLogged">
            <div class="nav-user-menu" style="display: flex; align-items: center; gap: 10px;">
              <a routerLink="/mis-reservas" class="btn-outline nav-user-btn" style="padding: 6px 14px; text-decoration: none;">
                {{ user.name ? user.name.split(' ')[0] : 'Huésped' }}
              </a>
              <a *ngIf="user.role === 'ADMIN' || user.role === 'admin'" routerLink="/admin" class="btn-outline" style="padding: 6px 14px; text-decoration: none;">
                Admin
              </a>
              <button class="link-btn" (click)="auth.logout()" style="background: none; border: none; color: #c9a96e; cursor: pointer;">
                Salir
              </button>
            </div>
          </ng-container>
          <ng-template #notLogged>
            <div style="display: flex; align-items: center; gap: 12px;">
              <button class="link-btn" (click)="openAuth('login')" style="background: none; border: none; color: #f5f0e8; cursor: pointer; font-size: 0.85rem;">
                Iniciar sesión
              </button>
              <button class="btn-outline" (click)="openAuth('register')" style="padding: 6px 16px; cursor: pointer;">
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
