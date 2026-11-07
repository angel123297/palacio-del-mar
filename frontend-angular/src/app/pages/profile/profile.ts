import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 4rem 1.5rem; display: flex; justify-content: center;">
      <div style="max-width: 600px; width: 100%;">
        <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); margin-bottom: 2rem; text-align: center;">Mi Perfil</h1>

        <div *ngIf="loading" class="luxury-loader-container" style="min-height: 200px;">
          <div class="luxury-spinner"></div>
          <p class="luxury-loader-text">Cargando...</p>
        </div>

        <div *ngIf="!loading">
          <!-- Profile Edit Form -->
          <form [formGroup]="profileForm" (ngSubmit)="updateProfile()" style="background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem; margin-bottom: 2rem;">
            <h3 style="color: #fff; margin-bottom: 1.5rem; font-size: 1.2rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 0.5rem;">Datos Personales</h3>
            
            <div style="margin-bottom: 1.2rem;">
              <label for="profile-name" style="display: block; color: #c9a96e; margin-bottom: 0.5rem; font-size: 0.9rem; font-weight: 500;">Nombre Completo</label>
              <input id="profile-name" type="text" formControlName="name" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.15); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
            </div>

            <div style="margin-bottom: 1.5rem;">
              <label for="profile-email" style="display: block; color: #c9a96e; margin-bottom: 0.5rem; font-size: 0.9rem; font-weight: 500;">Correo Electrónico (No modificable)</label>
              <input id="profile-email" type="email" formControlName="email" readonly style="width: 100%; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.15); color: #aaa; padding: 12px; border-radius: 6px; font-size: 1rem; cursor: not-allowed;">
            </div>

            <button type="submit" [disabled]="profileForm.invalid || savingProfile" class="btn-primary" style="width: 100%; padding: 14px; font-size: 1rem; display: flex; justify-content: center; gap: 10px; min-height: 44px;">
              <span *ngIf="!savingProfile">Actualizar Perfil</span>
              <span *ngIf="savingProfile">Guardando...</span>
              <div *ngIf="savingProfile" class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
            </button>
            <p *ngIf="profileSuccess" role="status" style="color: #2ecc71; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ profileSuccess }}</p>
            <p *ngIf="profileError" role="alert" style="color: #ff4d4d; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ profileError }}</p>
          </form>

          <!-- Change Password Form -->
          <form [formGroup]="passwordForm" (ngSubmit)="changePassword()" style="background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem;">
            <h3 style="color: #fff; margin-bottom: 1.5rem; font-size: 1.2rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 0.5rem;">Cambiar Contraseña</h3>
            
            <div style="margin-bottom: 1.2rem;">
              <label for="profile-current-password" style="display: block; color: #c9a96e; margin-bottom: 0.5rem; font-size: 0.9rem; font-weight: 500;">Contraseña Actual</label>
              <div style="position: relative; display: flex; align-items: center;">
                <input id="profile-current-password" [type]="showCurrentPassword ? 'text' : 'password'" formControlName="currentPassword" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.15); color: #fff; padding: 12px 42px 12px 12px; border-radius: 6px; font-size: 1rem;">
                <button type="button" (click)="showCurrentPassword = !showCurrentPassword" [attr.aria-label]="showCurrentPassword ? 'Ocultar contraseña' : 'Ver contraseña'" style="position: absolute; right: 4px; background: none; border: none; color: #c9a96e; cursor: pointer; padding: 8px; display: inline-flex; align-items: center; justify-content: center; min-height: 40px; min-width: 40px;">
                  <svg *ngIf="showCurrentPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                  </svg>
                  <svg *ngIf="!showCurrentPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                    <line x1="1" y1="1" x2="23" y2="23"></line>
                  </svg>
                </button>
              </div>
            </div>

            <div style="margin-bottom: 1.5rem;">
              <label for="profile-new-password" style="display: block; color: #c9a96e; margin-bottom: 0.5rem; font-size: 0.9rem; font-weight: 500;">Nueva Contraseña</label>
              <div style="position: relative; display: flex; align-items: center;">
                <input id="profile-new-password" [type]="showNewPassword ? 'text' : 'password'" formControlName="newPassword" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.15); color: #fff; padding: 12px 42px 12px 12px; border-radius: 6px; font-size: 1rem;">
                <button type="button" (click)="showNewPassword = !showNewPassword" [attr.aria-label]="showNewPassword ? 'Ocultar contraseña' : 'Ver contraseña'" style="position: absolute; right: 4px; background: none; border: none; color: #c9a96e; cursor: pointer; padding: 8px; display: inline-flex; align-items: center; justify-content: center; min-height: 40px; min-width: 40px;">
                  <svg *ngIf="showNewPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                  </svg>
                  <svg *ngIf="!showNewPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                    <line x1="1" y1="1" x2="23" y2="23"></line>
                  </svg>
                </button>
              </div>
            </div>

            <button type="submit" [disabled]="passwordForm.invalid || savingPassword" class="btn-primary" style="width: 100%; padding: 14px; font-size: 1rem; display: flex; justify-content: center; gap: 10px; min-height: 44px;">
              <span *ngIf="!savingPassword">Cambiar Contraseña</span>
              <span *ngIf="savingPassword">Actualizando...</span>
              <div *ngIf="savingPassword" class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
            </button>
            <p *ngIf="passwordSuccess" role="status" style="color: #2ecc71; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ passwordSuccess }}</p>
            <p *ngIf="passwordError" role="alert" style="color: #ff4d4d; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ passwordError }}</p>
          </form>

          <!-- Global Device Session Security (Redis Pillar 7) -->
          <section aria-label="Seguridad de sesiones activas" style="background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem; margin-top: 2rem;">
            <h3 style="color: #fff; margin: 0 0 0.75rem 0; font-size: 1.2rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 0.5rem;">Seguridad de Sesiones · Dispositivos Conectados</h3>
            <p style="color: #d6ceb8; font-size: 0.9rem; line-height: 1.5; margin-bottom: 1.25rem;">
              ¿Iniciaste sesión en un computador compartido o cambiaste de teléfono? Revoca de inmediato todos tus tokens activos en otros navegadores y dispositivos móviles.
            </p>
            <button
              id="btn-logout-all-devices"
              type="button"
              (click)="logoutAllDevices()"
              [disabled]="revokingAllSessions"
              aria-label="Cerrar sesión en todos los dispositivos"
              style="width: 100%; min-height: 44px; padding: 12px 16px; border-radius: 6px; border: 1px solid #e74c3c; background: rgba(231, 76, 60, 0.14); color: #ffb3ad; font-size: 0.95rem; font-weight: 600; cursor: pointer; display: flex; justify-content: center; align-items: center; gap: 10px;">
              <span *ngIf="!revokingAllSessions">Cerrar sesión en todos los dispositivos</span>
              <span *ngIf="revokingAllSessions">Revocando sesiones activas...</span>
            </button>
            <p *ngIf="sessionsSuccess" role="status" style="color: #2ecc71; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ sessionsSuccess }}</p>
            <p *ngIf="sessionsError" role="alert" style="color: #ff4d4d; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ sessionsError }}</p>
          </section>
        </div>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  loading = true;
  savingProfile = false;
  savingPassword = false;
  revokingAllSessions = false;

  profileSuccess = '';
  profileError = '';
  passwordSuccess = '';
  passwordError = '';
  sessionsSuccess = '';
  sessionsError = '';
  showCurrentPassword = false;
  showNewPassword = false;

  profileForm: FormGroup;
  passwordForm: FormGroup;

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
    private router: Router,
  ) {
    this.profileForm = this.fb.group({
      name: ['', Validators.required],
      email: ['']
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  ngOnInit() {
    const current = this.auth.currentUser();
    if (current) {
      this.profileForm.patchValue({
        name: current.name || '',
        email: current.email || ''
      });
    }

    this.api.get<any>('/auth/me').pipe(
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        const user = res.user || (res.data && res.data.user);
        if (user) {
           this.profileForm.patchValue({
             name: user.name,
             email: user.email
           });
        }
      },
      error: () => {
        if (!current) {
          this.profileError = 'No se pudo cargar el perfil.';
        }
      }
    });
  }

  updateProfile() {
    if (this.profileForm.invalid) return;

    this.savingProfile = true;
    this.profileSuccess = '';
    this.profileError = '';
    this.cdr.detectChanges();

    this.api.put<any>('/auth/profile', { name: this.profileForm.value.name }).pipe(
      finalize(() => {
        this.savingProfile = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.profileSuccess = 'Perfil actualizado exitosamente.';
        // Clear message after 3s
        setTimeout(() => { this.profileSuccess = ''; this.cdr.detectChanges(); }, 3000);
      },
      error: () => {
        this.profileError = 'Error al actualizar perfil.';
      }
    });
  }

  changePassword() {
    if (this.passwordForm.invalid) return;

    this.savingPassword = true;
    this.passwordSuccess = '';
    this.passwordError = '';
    this.cdr.detectChanges();

    this.api.put<any>('/auth/change-password', this.passwordForm.value).pipe(
      finalize(() => {
        this.savingPassword = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.passwordSuccess = 'Contraseña actualizada exitosamente.';
        this.passwordForm.reset();
        setTimeout(() => { this.passwordSuccess = ''; this.cdr.detectChanges(); }, 3000);
      },
      error: () => {
        this.passwordError = 'Error al cambiar contraseña. Verifica tu contraseña actual.';
      }
    });
  }

  logoutAllDevices() {
    this.revokingAllSessions = true;
    this.sessionsSuccess = '';
    this.sessionsError = '';
    this.cdr.detectChanges();

    this.auth.logoutAllDevices().pipe(
      finalize(() => {
        this.revokingAllSessions = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        this.sessionsSuccess = res?.message || 'Se han cerrado todas tus sesiones activas.';
        this.cdr.detectChanges();
        setTimeout(() => {
          this.router.navigate(['/']);
        }, 900);
      },
      error: (err) => {
        this.sessionsError = err?.error?.message || 'No se pudieron cerrar las sesiones. Intenta nuevamente.';
      }
    });
  }
}
