import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <nav class="navbar">
      <div class="logo"><a routerLink="/">Palacio del Mar</a></div>
      <div class="links">
        <a routerLink="/rooms">Suites</a>
        <a routerLink="/experiences">Experiencias</a>
      </div>
      <div class="auth">
        <ng-container *ngIf="auth.currentUser$ | async as user; else notLogged">
          <span>{{user.name}}</span>
          <a routerLink="/my-bookings">Mis Reservas</a>
          <a *ngIf="user.role === 'ADMIN'" routerLink="/admin">Admin</a>
          <button (click)="auth.logout()">Salir</button>
        </ng-container>
        <ng-template #notLogged>
          <button>Iniciar Sesión</button>
        </ng-template>
      </div>
    </nav>
  `,
  styles: [`
    .navbar { display: flex; justify-content: space-between; padding: 1rem 2rem; background: #0d0d0d; color: #d4af37; border-bottom: 1px solid #d4af37; }
    .navbar a { color: #d4af37; text-decoration: none; margin: 0 1rem; }
    .navbar button { background: #d4af37; color: #0d0d0d; border: none; padding: 0.5rem 1rem; cursor: pointer; }
  `]
})
export class NavbarComponent {
  constructor(public auth: AuthService) {}
}
