import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, tap, timeout } from 'rxjs';
import { User, AuthResponse } from '../models/types';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = environment.apiUrl + '/auth';
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();
  private readonly TIMEOUT_MS = 15000;

  get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  isLoggedIn(): boolean {
    return !!this.currentUserSubject.value;
  }

  constructor(private http: HttpClient) {
    // Try to load user from local cache immediately so UI doesn't flicker
    const cachedUser = localStorage.getItem('user');
    if (cachedUser) {
      try {
        this.currentUserSubject.next(JSON.parse(cachedUser));
      } catch (e) {
        localStorage.removeItem('user');
      }
    }

    // Verify actual session with the backend asynchronously
    this.fetchMe();
  }

  private fetchMe() {
    this.http.get<any>(`${this.apiUrl}/me`).subscribe({
      next: (res) => {
        const user = res.user || res.data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSubject.next(user);
        } else {
          this.clearLocalData();
        }
      },
      error: () => {
        // If /me fails (e.g. 401 Unauthorized), we clear the local state
        this.clearLocalData();
      }
    });
  }

  private clearLocalData() {
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
  }

  login(credentials: any) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
      timeout(this.TIMEOUT_MS),
      tap(res => {
        const user = res.user || (res as any).data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSubject.next(user);
        }
      })
    );
  }

  register(data: any) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data).pipe(
      timeout(this.TIMEOUT_MS),
      tap(res => {
        const user = res.user || (res as any).data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSubject.next(user);
        }
      })
    );
  }

  logout() {
    // Also notify backend to clear the HttpOnly cookie
    return this.http.post(`${this.apiUrl}/logout`, {}).pipe(
      tap(() => {
        this.clearLocalData();
      })
    ).subscribe({
      error: () => {
        // Even if the server request fails, clear local state
        this.clearLocalData();
      }
    });
  }
}
