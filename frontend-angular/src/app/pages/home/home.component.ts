import { Component } from '@angular/core';
import { HeroComponent } from '../../components/hero/hero.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [HeroComponent],
  template: `
    <app-hero></app-hero>
    <div class="container" style="padding: 2rem;">
      <h2 style="color: #d4af37">Nuestras Sucursales</h2>
      <p style="color: white">Cartagena, Getsemaní, Bocagrande, La Boquilla</p>
    </div>
  `
})
export class HomeComponent {}
