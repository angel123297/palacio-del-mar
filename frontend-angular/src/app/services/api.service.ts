import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable, timeout } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly TIMEOUT_MS = 15000;

  constructor(private http: HttpClient) {}

  get<T>(path: string, params?: Record<string, string | number | boolean>): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] !== undefined && params[key] !== null) {
          httpParams = httpParams.set(key, params[key]);
        }
      });
    }
    return this.http.get<T>(`${environment.apiUrl}${path}`, { params: httpParams }).pipe(timeout(this.TIMEOUT_MS));
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(`${environment.apiUrl}${path}`, body).pipe(timeout(this.TIMEOUT_MS));
  }
  
  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(`${environment.apiUrl}${path}`, body).pipe(timeout(this.TIMEOUT_MS));
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`${environment.apiUrl}${path}`).pipe(timeout(this.TIMEOUT_MS));
  }

  getBlob(path: string): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}${path}`, { responseType: 'blob' }).pipe(timeout(this.TIMEOUT_MS));
  }
}
