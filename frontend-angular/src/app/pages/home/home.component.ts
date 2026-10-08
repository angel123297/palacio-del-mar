import { Component } from '@angular/core';
import { HeroComponent } from '../../components/hero/hero.component';
import { BranchSelectorComponent } from '../../components/branch-selector/branch-selector.component';
import { RoomsSectionComponent } from '../../components/rooms-section/rooms-section.component';
import { ExperiencesSectionComponent } from '../../components/experiences-section/experiences-section.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [HeroComponent, BranchSelectorComponent, RoomsSectionComponent, ExperiencesSectionComponent],
  template: `
    <app-hero></app-hero>
    <app-branch-selector></app-branch-selector>
    <app-rooms-section></app-rooms-section>
    <app-experiences-section></app-experiences-section>
  `
})
export class HomeComponent {}
