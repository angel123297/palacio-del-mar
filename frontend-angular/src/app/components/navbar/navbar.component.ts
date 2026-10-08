import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AuthModalComponent } from '../auth-modal/auth-modal.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, AuthModalComponent],
  template: `
    <nav class="navbar">
      <div class="logo"><a routerLink="/">Palacio del Mar</a></div>
      <div class="links">
        <a routerLink="/#rooms">Suites</a>
        <a routerLink="/#experiences">Experiencias</a>
      </div>
      <div class="auth">
        <ng-container *ngIf="auth.currentUser$ | async as user; else notLogged">
          <span style="margin-right: 12px;">Hola, {{user.name}}</span>
          <a routerLink="/mis-reservas">Mis Reservas</a>
          <a *ngIf="user.role === 'ADMIN'" routerLink="/admin">Admin</a>
          <button (click)="auth.logout()" style="margin-left: 12px;">Salir</button>
        </ng-container>
        <ng-template #notLogged>
          <button (click)="authModalOpen = true">Iniciar Sesión</button>
        </ng-template>
      </div>
    </nav>
    <app-auth-modal [isOpen]="authModalOpen" (closeEvent)="authModalOpen = false"></app-auth-modal>
  `,
  styles: [`
    .navbar { display: flex; justify-content: space-between; align-items: center; padding: 1rem 2rem; background: #0d0d0d; color: #d4af37; border-bottom: 1px solid rgba(212,175,55,0.3); }
    .navbar .logo a { font-family: var(--font-title); font-size: 1.4rem; font-weight: 700; color: #d4af37; text-decoration: none; }
    .navbar a { color: #ffffff; text-decoration: none; margin: 0 1rem; font-size: 0.95rem; }
    .navbar a:hover { color: #d4af37; }
    .navbar button { background: #d4af37; color: #0d0d0d; font-weight: 700; border: none; padding: 0.5rem 1.2rem; border-radius: 6px; cursor: pointer; }
  `]
})
export class NavbarComponent {
  authModalOpen = false;

  constructor(public auth: AuthService) {}
}
