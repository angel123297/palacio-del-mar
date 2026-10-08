import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Experience } from '../models/types';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExperienceService {
  constructor(private api: ApiService) {}
  getExperiences(): Observable<Experience[]> { return this.api.get<Experience[]>('/experiences'); }
}
