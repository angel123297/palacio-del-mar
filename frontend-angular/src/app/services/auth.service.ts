import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap, timeout } from 'rxjs';
import { User, AuthResponse, LoginDTO, RegisterDTO } from '../models/types';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = environment.apiUrl + '/auth';
  private currentUserSignal = signal<User | null>(null);
  currentUser = this.currentUserSignal.asReadonly();
  private readonly TIMEOUT_MS = 15000;

  get currentUserValue(): User | null {
    return this.currentUserSignal();
  }

  isLoggedIn(): boolean {
    const user = this.currentUserSignal();
    return !!user && user.emailVerified !== false;
  }

  constructor(private http: HttpClient) {
    this.loadCachedUser();
    this.fetchMe();
  }

  loadCachedUser() {
    const cachedUser = localStorage.getItem('user');
    if (cachedUser) {
      try {
        this.currentUserSignal.set(JSON.parse(cachedUser));
      } catch (e) {
        localStorage.removeItem('user');
      }
    } else {
      this.currentUserSignal.set(null);
    }
  }

  private fetchMe() {
    const token = localStorage.getItem('token');
    const cachedUser = localStorage.getItem('user');
    if (!token && !cachedUser) {
      return;
    }

    this.http.get<AuthResponse>(`${this.apiUrl}/me`).subscribe({
      next: (res) => {
        const user = res.user || res.data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSignal.set(user);
        }
      },
      error: (err) => {
        if (err?.status === 401) {
          this.clearLocalData();
        }
      }
    });
  }

  clearLocalData() {
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    this.currentUserSignal.set(null);
  }

  login(credentials: LoginDTO) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
      timeout(this.TIMEOUT_MS),
      tap(res => {
        const token = (res as any).token || (res as any).data?.token;
        if (token) {
          localStorage.setItem('token', token);
        }
        const user = res.user || res.data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSignal.set(user);
        }
      })
    );
  }

  register(data: RegisterDTO) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data).pipe(
      timeout(this.TIMEOUT_MS),
      tap(res => {
        const token = (res as any).token || (res as any).data?.token;
        if (token) {
          localStorage.setItem('token', token);
        }
        const user = res.user || res.data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSignal.set(user);
        }
      })
    );
  }

  verifyEmail(token: string) {
    return this.http.post<{success?: boolean, message?: string}>(`${this.apiUrl}/verify-email`, { token }).pipe(
      timeout(this.TIMEOUT_MS)
    );
  }

  forceVerify(email: string) {
    return this.http.post<{success?: boolean, message?: string}>(`${this.apiUrl}/force-verify`, { email }).pipe(
      timeout(this.TIMEOUT_MS)
    );
  }

  logout() {
    return this.http.post(`${this.apiUrl}/logout`, {}).pipe(
      tap(() => {
        this.clearLocalData();
      })
    ).subscribe({
      error: () => {
        this.clearLocalData();
      }
    });
  }
}
