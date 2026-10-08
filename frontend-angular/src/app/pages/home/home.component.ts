import { Component } from '@angular/core';
import { HeroComponent } from '../../components/hero/hero.component';
import { BookingBarComponent } from '../../components/booking-bar/booking-bar.component';
import { BranchSelectorComponent } from '../../components/branch-selector/branch-selector.component';
import { RoomsSectionComponent } from '../../components/rooms-section/rooms-section.component';
import { ExperiencesSectionComponent } from '../../components/experiences-section/experiences-section.component';
import { DiningSectionComponent } from '../../components/dining-section/dining-section.component';
import { SpaSectionComponent } from '../../components/spa-section/spa-section.component';
import { TestimonialsSectionComponent } from '../../components/testimonials-section/testimonials-section.component';
import { FaqSectionComponent } from '../../components/faq-section/faq-section.component';
import { ContactSectionComponent } from '../../components/contact-section/contact-section.component';
import { AIChatWidgetComponent } from '../../components/ai-chat-widget/ai-chat-widget.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    HeroComponent,
    BookingBarComponent,
    BranchSelectorComponent,
    RoomsSectionComponent,
    ExperiencesSectionComponent,
    DiningSectionComponent,
    SpaSectionComponent,
    TestimonialsSectionComponent,
    FaqSectionComponent,
    ContactSectionComponent,
    AIChatWidgetComponent
  ],
  template: `
    <app-hero></app-hero>
    <app-booking-bar></app-booking-bar>
    <app-branch-selector></app-branch-selector>
    <app-rooms-section></app-rooms-section>
    <app-experiences-section></app-experiences-section>
    <app-dining-section></app-dining-section>
    <app-spa-section></app-spa-section>
    <app-testimonials-section></app-testimonials-section>
    <app-faq-section></app-faq-section>
    <app-contact-section></app-contact-section>
    <app-ai-chat-widget></app-ai-chat-widget>
  `
})
export class HomeComponent {}
