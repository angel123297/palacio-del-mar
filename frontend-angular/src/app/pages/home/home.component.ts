import { HotelMapComponent } from '../../components/hotel-map/hotel-map.component';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { HeroComponent } from '../../components/hero/hero.component';
import { BookingBarComponent } from '../../components/booking-bar/booking-bar.component';
import { BranchSelectorComponent } from '../../components/branch-selector/branch-selector.component';
import { RoomsSectionComponent } from '../../components/rooms-section/rooms-section.component';
import { FaqSectionComponent } from '../../components/faq-section/faq-section.component';
import { ContactSectionComponent } from '../../components/contact-section/contact-section.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    HeroComponent,
    BookingBarComponent,
    BranchSelectorComponent,
    RoomsSectionComponent,
    FaqSectionComponent,
    ContactSectionComponent,
    HotelMapComponent
  ],
  template: `
    <!-- Banner informativo para administradores o anfitriones explorando la web pública -->
    <div *ngIf="isCorporateUser" style="background: rgba(201, 169, 110, 0.12); border-bottom: 1px solid rgba(201, 169, 110, 0.35); padding: 8px 16px; text-align: center; font-size: 0.85rem; color: #f5f0e8; display: flex; align-items: center; justify-content: center; gap: 12px; flex-wrap: wrap;">
      <span>👑 Modo vista previa pública ({{ corporateRoleLabel }})</span>
      <a [routerLink]="corporatePortalUrl" style="color: var(--gold); font-weight: 700; text-decoration: none; border: 1px solid var(--gold); padding: 3px 12px; border-radius: 4px; font-size: 0.8rem; background: rgba(0,0,0,0.4);">
        Ir a mi Panel de Control &rarr;
      </a>
    </div>

    <app-hero></app-hero>
    <app-booking-bar></app-booking-bar>
    <app-branch-selector></app-branch-selector>
    <app-hotel-map></app-hotel-map>
    <app-rooms-section></app-rooms-section>
    <app-faq-section></app-faq-section>
    <app-contact-section></app-contact-section>
  `
})
export class HomeComponent {
  constructor(private auth: AuthService) {}

  get isCorporateUser(): boolean {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    return role === 'admin' || role === 'host' || role === 'anfitrion';
  }

  get corporateRoleLabel(): string {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    return role === 'admin' ? 'Administrador' : 'Anfitrión';
  }

  get corporatePortalUrl(): string {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    return role === 'admin' ? '/admin' : '/anfitrion';
  }
}
