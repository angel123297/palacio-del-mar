import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Branch } from '../models/types';
import { Observable, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BranchService {
  constructor(private api: ApiService) {}
  getBranches(): Observable<Branch[]> {
    return this.api.get<any>('/branches').pipe(
      map(res => (res?.data || res) as Branch[])
    );
  }
}
