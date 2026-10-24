import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { HeroComponent } from '../../components/hero/hero.component';
import { BookingBarComponent } from '../../components/booking-bar/booking-bar.component';
import { BranchSelectorComponent } from '../../components/branch-selector/branch-selector.component';
import { RoomsSectionComponent } from '../../components/rooms-section/rooms-section.component';
import { FaqSectionComponent } from '../../components/faq-section/faq-section.component';
import { ContactSectionComponent } from '../../components/contact-section/contact-section.component';
import { HotelMapComponent } from '../../components/hotel-map/hotel-map.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    HeroComponent,
    BookingBarComponent,
    BranchSelectorComponent,
    RoomsSectionComponent,
    FaqSectionComponent,
    ContactSectionComponent,
    HotelMapComponent
  ],
  template: `
    <ng-container *ngIf="!isCorporateUser">
      <app-hero></app-hero>
      <app-booking-bar></app-booking-bar>
      <app-branch-selector></app-branch-selector>
      
      <!-- Aquí está el mapa con el recuadro rojo temporal para ubicarlo -->
      <app-hotel-map style="display: block; width: 100%; max-width: 1200px; height: 500px; background-color: #111; border: 2px solid red; margin: 20px auto;"></app-hotel-map>

      <app-rooms-section></app-rooms-section>
      <app-faq-section></app-faq-section>
      <app-contact-section></app-contact-section>
    </ng-container>
  `
})
export class HomeComponent implements OnInit {
  constructor(private auth: AuthService, private router: Router) {}

  get isCorporateUser(): boolean {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    return role === 'admin' || role === 'host' || role === 'anfitrion';
  }

  ngOnInit(): void {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    if (role === 'admin') {
      this.router.navigate(['/admin']);
    } else if (role === 'host' || role === 'anfitrion') {
      this.router.navigate(['/anfitrion']);
    }
  }
}