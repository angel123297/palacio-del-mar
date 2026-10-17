import { Component } from '@angular/core';
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
    HeroComponent,
    BookingBarComponent,
    BranchSelectorComponent,
    RoomsSectionComponent,
    FaqSectionComponent,
    ContactSectionComponent
  ],
  template: `
    <app-hero></app-hero>
    <app-booking-bar></app-booking-bar>
    <app-branch-selector></app-branch-selector>
    <app-rooms-section></app-rooms-section>
    <app-faq-section></app-faq-section>
    <app-contact-section></app-contact-section>
  `
})
export class HomeComponent {}
