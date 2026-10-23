import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { FooterComponent } from './components/footer/footer.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent],
  template: `
    <a href="#main-content" class="skip-link">Saltar al contenido principal</a>
    <app-navbar></app-navbar>
    <main id="main-content" tabindex="-1">
      <router-outlet></router-outlet>
    </main>
    <app-footer></app-footer>
  `,
  styleUrls: ['./app.scss']
})
export class App {
  title = 'frontend-angular';
}
