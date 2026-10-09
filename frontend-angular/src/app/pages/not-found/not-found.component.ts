import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="min-height: 80vh; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; color: white;">
      <h1 style="font-size: 6rem; color: var(--gold); margin-bottom: 1rem;">404</h1>
      <h2 style="font-size: 2rem; margin-bottom: 2rem;">Página no encontrada</h2>
      <p style="margin-bottom: 2rem; color: #ccc;">Lo sentimos, la página que buscas no existe o ha sido movida.</p>
      <a routerLink="/" class="btn-primary" style="padding: 10px 20px; text-decoration: none;">Volver al inicio</a>
    </div>
  `
})
export class NotFoundComponent {}
