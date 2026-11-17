import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, filter, finalize, map, of, take, tap, timeout } from 'rxjs';
import { User, AuthResponse, LoginDTO, RegisterDTO } from '../models/types';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = environment.apiUrl + '/auth';
  private currentUserSignal = signal<User | null>(null);
  currentUser = this.currentUserSignal.asReadonly();
  private readonly TIMEOUT_MS = 15000;
  private sessionEpoch = 0;
  private sessionInitialized$ = new BehaviorSubject<boolean>(false);

  get currentUserValue(): User | null {
    return this.currentUserSignal();
  }

  isLoggedIn(): boolean {
    const user = this.currentUserSignal();
    return !!user && user.emailVerified === true;
  }

  ensureSession(): Observable<User | null> {
    return this.sessionInitialized$.pipe(
      filter(init => init === true),
      take(1),
      map(() => this.currentUserSignal())
    );
  }

  constructor(private http: HttpClient, private router: Router) {
    this.loadCachedUser();
    this.fetchMe();
  }

  loadCachedUser() {
    const cachedUser = localStorage.getItem('user');
    const token = localStorage.getItem('token');
    if (cachedUser && token) {
      try {
        const user = JSON.parse(cachedUser);
        if (user && user.emailVerified === true) {
          this.currentUserSignal.set(user);
          return;
        }
      } catch (e) {
        // Formato JSON invalido
      }
    }
    this.clearLocalData();
  }

  private fetchMe() {
    const token = localStorage.getItem('token');
    const cachedUser = localStorage.getItem('user');
    if (!token && !cachedUser) {
      this.sessionInitialized$.next(true);
      return;
    }

    const currentEpoch = this.sessionEpoch;
    this.http.get<AuthResponse>(`${this.apiUrl}/me`).subscribe({
      next: (res) => {
        if (currentEpoch !== this.sessionEpoch) return;
        const user = res.user || res.data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSignal.set(user);
        }
        this.sessionInitialized$.next(true);
      },
      error: (err) => {
        if (currentEpoch !== this.sessionEpoch) return;
        if (err?.status === 401) {
          this.clearLocalData();
        }
        this.sessionInitialized$.next(true);
      }
    });
  }

  clearLocalData() {
    this.sessionEpoch++;
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    this.currentUserSignal.set(null);
  }

  updateCurrentUser(partial: Partial<User>) {
    const current = this.currentUserSignal();
    const updated = { ...(current || {}), ...partial } as User;
    if (updated && (updated.id || (updated as any)._id)) {
      if (!updated.id && (updated as any)._id) {
        updated.id = (updated as any)._id;
      }
      localStorage.setItem('user', JSON.stringify(updated));
      this.currentUserSignal.set(updated);
    }
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
        const user = res.user || res.data?.user;
        if (token && user?.emailVerified === true) {
          localStorage.setItem('token', token);
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSignal.set(user);
        } else {
          // El usuario aun no ha verificado su correo: limpiar cualquier residuo de sesion
          this.clearLocalData();
        }
      })
    );
  }

  verifyEmail(token: string) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/verify-email`, { token }).pipe(
      timeout(this.TIMEOUT_MS),
      tap(res => {
        const tokenVal = (res as any).token || (res as any).data?.token;
        if (tokenVal) {
          localStorage.setItem('token', tokenVal);
        }
        const user = res.user || (res as any).data?.user;
        if (user) {
          localStorage.setItem('user', JSON.stringify(user));
          this.currentUserSignal.set(user);
        }
      })
    );
  }

  resendVerification(email: string) {
    return this.http.post<{ success?: boolean; message?: string }>(`${this.apiUrl}/resend-verification`, { email }).pipe(
      timeout(this.TIMEOUT_MS)
    );
  }

  forgotPassword(email: string) {
    return this.http.post<{ success?: boolean; message?: string }>(`${this.apiUrl}/forgot-password`, { email }).pipe(
      timeout(this.TIMEOUT_MS)
    );
  }

  resetPassword(token: string, newPassword: string) {
    return this.http.post<{ success?: boolean; message?: string }>(`${this.apiUrl}/reset-password`, { token, newPassword }).pipe(
      timeout(this.TIMEOUT_MS)
    );
  }

  logout() {
    const token = localStorage.getItem('token');
    this.clearLocalData();
    this.router.navigate(['/']);
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};
    return this.http.post(`${this.apiUrl}/logout`, {}, { headers }).pipe(
      timeout(this.TIMEOUT_MS)
    ).subscribe({
      next: () => {},
      error: () => {}
    });
  }

  logoutAllDevices() {
    return this.http.post<{ success?: boolean; message?: string }>(`${this.apiUrl}/logout-all`, {}).pipe(
      timeout(this.TIMEOUT_MS),
      finalize(() => {
        this.clearLocalData();
      })
    );
  }
}
