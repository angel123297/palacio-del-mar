import { Component } from '@angular/core';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { HeroComponent } from '../../components/hero/hero.component';
import { RoomsSectionComponent } from '../../components/rooms-section/rooms-section.component';
import { ExperiencesSectionComponent } from '../../components/experiences-section/experiences-section.component';
import { FooterComponent } from '../../components/footer/footer.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [NavbarComponent, HeroComponent, RoomsSectionComponent, ExperiencesSectionComponent, FooterComponent],
  template: `
    <app-navbar></app-navbar>
    <app-hero></app-hero>
    <app-rooms-section></app-rooms-section>
    <app-experiences-section></app-experiences-section>
    <app-footer></app-footer>
  `
})
export class HomeComponent {}
