import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Experience } from '../models/types';
import { Observable, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExperienceService {
  constructor(private api: ApiService) {}
  getExperiences(): Observable<Experience[]> {
    return this.api.get<any>('/experiences').pipe(
      map(res => (res?.data || res) as Experience[])
    );
  }
}
