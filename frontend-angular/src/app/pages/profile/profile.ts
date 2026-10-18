import { Component, OnInit, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ApiService } from '../../services/api.service';
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
              <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Nombre Completo</label>
              <input type="text" formControlName="name" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
            </div>

            <div style="margin-bottom: 1.5rem;">
              <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Correo Electrónico (No modificable)</label>
              <input type="email" formControlName="email" readonly style="width: 100%; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); color: #888; padding: 12px; border-radius: 6px; font-size: 1rem; cursor: not-allowed;">
            </div>

            <button type="submit" [disabled]="profileForm.invalid || savingProfile" class="btn-primary" style="width: 100%; padding: 14px; font-size: 1rem; display: flex; justify-content: center; gap: 10px;">
              <span *ngIf="!savingProfile">Actualizar Perfil</span>
              <span *ngIf="savingProfile">Guardando...</span>
              <div *ngIf="savingProfile" class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
            </button>
            <p *ngIf="profileSuccess" style="color: #2ecc71; margin-top: 1rem; text-align: center;">{{ profileSuccess }}</p>
            <p *ngIf="profileError" style="color: #ff4d4d; margin-top: 1rem; text-align: center;">{{ profileError }}</p>
          </form>

          <!-- Change Password Form -->
          <form [formGroup]="passwordForm" (ngSubmit)="changePassword()" style="background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem;">
            <h3 style="color: #fff; margin-bottom: 1.5rem; font-size: 1.2rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 0.5rem;">Cambiar Contraseña</h3>
            
            <div style="margin-bottom: 1.2rem;">
              <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Contraseña Actual</label>
              <input type="password" formControlName="currentPassword" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
            </div>

            <div style="margin-bottom: 1.5rem;">
              <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Nueva Contraseña</label>
              <input type="password" formControlName="newPassword" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
            </div>

            <button type="submit" [disabled]="passwordForm.invalid || savingPassword" class="btn-primary" style="width: 100%; padding: 14px; font-size: 1rem; display: flex; justify-content: center; gap: 10px;">
              <span *ngIf="!savingPassword">Cambiar Contraseña</span>
              <span *ngIf="savingPassword">Actualizando...</span>
              <div *ngIf="savingPassword" class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
            </button>
            <p *ngIf="passwordSuccess" style="color: #2ecc71; margin-top: 1rem; text-align: center;">{{ passwordSuccess }}</p>
            <p *ngIf="passwordError" style="color: #ff4d4d; margin-top: 1rem; text-align: center;">{{ passwordError }}</p>
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
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {
    this.profileForm = this.fb.group({
      name: ['', Validators.required],
      email: ['']
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  ngOnInit() {
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
         this.profileError = 'No se pudo cargar el perfil.';
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
