#!/bin/bash
cd /home/angel19/Notas/palacio-del-mar/frontend-angular

mkdir -p src/app/models src/app/services src/app/interceptors src/app/components src/app/pages

# Models
cat << 'MODELS' > src/app/models/types.ts
export interface Branch {
  id: string;
  name: string;
  slug: string;
  location: string;
}

export interface Suite {
  id: string;
  name: string;
  slug: string;
  description: string;
  pricePerNight: number;
  capacity: number;
  amenities: string[];
  branchId: string;
  branch?: Branch;
  images: string[];
}

export interface Experience {
  id: string;
  title: string;
  description: string;
  price: number;
  imageUrl: string;
}

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Booking {
  id: string;
  userId: string;
  suiteId: string;
  checkIn: string;
  checkOut: string;
  totalPrice: number;
  status: string;
  suite?: Suite;
}
MODELS

# Interceptor
cat << 'INTERCEPTOR' > src/app/interceptors/jwt.interceptor.ts
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();
  if (token) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: \`Bearer \${token}\`
      }
    });
    return next(cloned);
  }
  return next(req);
};
INTERCEPTOR

# Services
cat << 'AUTH_SERVICE' > src/app/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, tap } from 'rxjs';
import { User, AuthResponse } from '../models/types';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = environment.apiUrl + '/auth';
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    const user = localStorage.getItem('user');
    if (user) {
      this.currentUserSubject.next(JSON.parse(user));
    }
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  login(credentials: any) {
    return this.http.post<AuthResponse>(\`\${this.apiUrl}/login\`, credentials).pipe(
      tap(res => {
        localStorage.setItem('token', res.token);
        localStorage.setItem('user', JSON.stringify(res.user));
        this.currentUserSubject.next(res.user);
      })
    );
  }

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
  }
}
AUTH_SERVICE

cat << 'API_SERVICE' > src/app/services/api.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  get<T>(path: string, params?: any): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] !== undefined && params[key] !== null) {
          httpParams = httpParams.set(key, params[key]);
        }
      });
    }
    return this.http.get<T>(\`\${environment.apiUrl}\${path}\`, { params: httpParams });
  }

  post<T>(path: string, body: any): Observable<T> {
    return this.http.post<T>(\`\${environment.apiUrl}\${path}\`, body);
  }
  
  put<T>(path: string, body: any): Observable<T> {
    return this.http.put<T>(\`\${environment.apiUrl}\${path}\`, body);
  }
}
API_SERVICE

mkdir -p src/environments
cat << 'ENV' > src/environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:3000/api'
};
ENV
