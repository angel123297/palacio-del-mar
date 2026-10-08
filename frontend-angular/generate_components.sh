#!/bin/bash
cd /home/angel19/Notas/palacio-del-mar/frontend-angular

mkdir -p src/app/components/{navbar,footer,hero,branch-selector,rooms-section,experiences-section,booking-modal,auth-modal}
mkdir -p src/app/pages/{home,suite-detail,my-bookings,admin-dashboard}

cat << 'NAV' > src/app/components/navbar/navbar.component.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: \`
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
  \`,
  styles: [\`
    .navbar { display: flex; justify-content: space-between; padding: 1rem 2rem; background: #0d0d0d; color: #d4af37; border-bottom: 1px solid #d4af37; }
    .navbar a { color: #d4af37; text-decoration: none; margin: 0 1rem; }
    .navbar button { background: #d4af37; color: #0d0d0d; border: none; padding: 0.5rem 1rem; cursor: pointer; }
  \`]
})
export class NavbarComponent {
  constructor(public auth: AuthService) {}
}
NAV

cat << 'FOOTER' > src/app/components/footer/footer.component.ts
import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: \`
    <footer class="footer">
      <p>&copy; 2026 Palacio del Mar. Todos los derechos reservados.</p>
    </footer>
  \`,
  styles: [\`
    .footer { text-align: center; padding: 2rem; background: #0d0d0d; color: #d4af37; border-top: 1px solid #d4af37; }
  \`]
})
export class FooterComponent {}
FOOTER

cat << 'HERO' > src/app/components/hero/hero.component.ts
import { Component } from '@angular/core';

@Component({
  selector: 'app-hero',
  standalone: true,
  template: \`
    <div class="hero">
      <h1>Lujo y confort frente al mar</h1>
      <p>Descubre nuestras exclusivas suites en Cartagena</p>
    </div>
  \`,
  styles: [\`
    .hero { text-align: center; padding: 5rem 2rem; background: #1a1a1a; color: white; }
    .hero h1 { color: #d4af37; }
  \`]
})
export class HeroComponent {}
HERO

cat << 'HOME' > src/app/pages/home/home.component.ts
import { Component } from '@angular/core';
import { HeroComponent } from '../../components/hero/hero.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [HeroComponent],
  template: \`
    <app-hero></app-hero>
    <div class="container" style="padding: 2rem;">
      <h2 style="color: #d4af37">Nuestras Sucursales</h2>
      <p style="color: white">Cartagena, Getsemaní, Bocagrande, La Boquilla</p>
    </div>
  \`
})
export class HomeComponent {}
HOME

