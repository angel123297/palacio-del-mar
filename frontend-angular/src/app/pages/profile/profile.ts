import { Component, OnInit, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
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
              <input id="profile-current-password" type="password" formControlName="currentPassword" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.15); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
            </div>

            <div style="margin-bottom: 1.5rem;">
              <label for="profile-new-password" style="display: block; color: #c9a96e; margin-bottom: 0.5rem; font-size: 0.9rem; font-weight: 500;">Nueva Contraseña</label>
              <input id="profile-new-password" type="password" formControlName="newPassword" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.15); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
            </div>

            <button type="submit" [disabled]="passwordForm.invalid || savingPassword" class="btn-primary" style="width: 100%; padding: 14px; font-size: 1rem; display: flex; justify-content: center; gap: 10px; min-height: 44px;">
              <span *ngIf="!savingPassword">Cambiar Contraseña</span>
              <span *ngIf="savingPassword">Actualizando...</span>
              <div *ngIf="savingPassword" class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
            </button>
            <p *ngIf="passwordSuccess" role="status" style="color: #2ecc71; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ passwordSuccess }}</p>
            <p *ngIf="passwordError" role="alert" style="color: #ff4d4d; margin-top: 1rem; text-align: center; font-size: 0.9rem;">{{ passwordError }}</p>
          </form>
        </div>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  loading = true;
  savingProfile = false;
  savingPassword = false;

  profileSuccess = '';
  profileError = '';
  passwordSuccess = '';
  passwordError = '';

  profileForm: FormGroup;
  passwordForm: FormGroup;

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
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
}
