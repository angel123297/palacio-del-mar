import { Component, EventEmitter, Input, Output, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="close()" role="dialog" aria-modal="true" [attr.aria-labelledby]="mode === 'login' ? 'login-modal-title' : (mode === 'register' ? 'reg-modal-title' : 'forgot-modal-title')" tabindex="-1" (keydown.escape)="close()" style="position: fixed; inset: 0; background: rgba(0,0,0,0.85); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 1rem;">
      <div class="modal-content" (click)="$event.stopPropagation()" style="background: var(--dark); border: 1px solid var(--gold); border-radius: 12px; padding: 2rem; width: 100%; max-width: 440px; color: var(--cream); position: relative;">
        
        <button type="button" aria-label="Cerrar modal de autenticación" (click)="close()" style="position: absolute; top: 12px; right: 12px; background: none; border: none; color: #aaa; font-size: 1.5rem; cursor: pointer; min-width: 44px; min-height: 44px; display: inline-flex; align-items: center; justify-content: center;">×</button>

        <!-- LOGIN FORM -->
        <form *ngIf="mode === 'login'" (ngSubmit)="submitLogin()" class="auth-form">
          <!-- Role Selector Tabs -->
          <div class="auth-role-tabs" style="display: flex; gap: 4px; margin-bottom: 1.25rem; background: rgba(255,255,255,0.05); padding: 4px; border-radius: 8px; border: 1px solid rgba(201,169,110,0.25);">
            <button type="button" (click)="selectLoginPortal('guest')"
                    [style.background]="selectedPortal === 'guest' ? 'var(--gold)' : 'transparent'"
                    [style.color]="selectedPortal === 'guest' ? '#0d0d0d' : '#f5f0e8'"
                    [style.font-weight]="selectedPortal === 'guest' ? '700' : '500'"
                    style="flex: 1; padding: 8px 4px; border: none; border-radius: 6px; font-size: 0.8rem; cursor: pointer; transition: all 0.2s; min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 4px;">
              🛎️ Huésped
            </button>
            <button type="button" (click)="selectLoginPortal('host')"
                    [style.background]="selectedPortal === 'host' ? 'var(--gold)' : 'transparent'"
                    [style.color]="selectedPortal === 'host' ? '#0d0d0d' : '#f5f0e8'"
                    [style.font-weight]="selectedPortal === 'host' ? '700' : '500'"
                    style="flex: 1; padding: 8px 4px; border: none; border-radius: 6px; font-size: 0.8rem; cursor: pointer; transition: all 0.2s; min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 4px;">
              🏢 Anfitrión
            </button>
            <button type="button" (click)="selectLoginPortal('admin')"
                    [style.background]="selectedPortal === 'admin' ? 'var(--gold)' : 'transparent'"
                    [style.color]="selectedPortal === 'admin' ? '#0d0d0d' : '#f5f0e8'"
                    [style.font-weight]="selectedPortal === 'admin' ? '700' : '500'"
                    style="flex: 1; padding: 8px 4px; border: none; border-radius: 6px; font-size: 0.8rem; cursor: pointer; transition: all 0.2s; min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 4px;">
              🔒 Admin
            </button>
          </div>

          <p class="modal-eyebrow" style="color: var(--gold); font-size: 0.75rem; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 4px;">{{ portalEyebrow }}</p>
          <h2 id="login-modal-title" class="modal-title" style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; margin: 0 0 0.4rem 0; color: #fff;">{{ portalTitle }}</h2>
          <p style="font-size: 0.85rem; color: #aaa; margin: 0 0 1.25rem 0; line-height: 1.4;">{{ portalDescription }}</p>
          
          <div style="margin-bottom: 1rem;">
            <label for="auth-login-email" class="field-label">Correo electrónico</label>
            <input id="auth-login-email" type="email" [(ngModel)]="email" name="email" required [placeholder]="portalPlaceholder" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label for="auth-login-password" class="field-label">Contraseña</label>
            <div style="position: relative; display: flex; align-items: center;">
              <input id="auth-login-password" [type]="showLoginPassword ? 'text' : 'password'" [(ngModel)]="password" name="password" required placeholder="••••••••" style="width: 100%; padding-right: 42px;" />
              <button type="button" (click)="showLoginPassword = !showLoginPassword" [attr.aria-label]="showLoginPassword ? 'Ocultar contraseña' : 'Ver contraseña'" style="position: absolute; right: 4px; background: none; border: none; color: var(--gold); cursor: pointer; padding: 8px; display: inline-flex; align-items: center; justify-content: center; min-height: 40px; min-width: 40px;">
                <svg *ngIf="showLoginPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
                <svg *ngIf="!showLoginPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                </svg>
              </button>
            </div>
          </div>

          <div style="text-align: right; margin-bottom: 1rem;">
            <button type="button" class="link-btn" (click)="mode = 'forgot'" style="background: none; border: none; color: var(--gold); font-size: 0.85rem; cursor: pointer; min-height: 44px; display: inline-flex; align-items: center;">
              ¿Olvidaste tu contraseña?
            </button>
          </div>

          <button type="submit" class="btn-primary" [disabled]="busy" style="width: 100%; padding: 12px; font-size: 1rem;">
            {{ busy ? 'Entrando…' : 'Iniciar sesión' }}
          </button>

          <p *ngIf="message" [attr.role]="isSuccess ? 'status' : 'alert'" aria-live="polite" [style.color]="isSuccess ? '#2ecc71' : '#ff4d4d'" [style.background]="isSuccess ? 'rgba(46, 204, 113, 0.1)' : 'rgba(255, 77, 77, 0.1)'" [style.border-color]="isSuccess ? 'rgba(46, 204, 113, 0.3)' : 'rgba(255, 77, 77, 0.3)'" style="margin-top: 1rem; text-align: center; font-size: 0.9rem; font-weight: 500; padding: 8px 12px; border-radius: 6px; border-width: 1px; border-style: solid; display: flex; flex-direction: column; align-items: center; gap: 8px;">
            <span>{{ message }}</span>
            <button *ngIf="showForceVerifyButton" type="button" (click)="forceVerify()" style="background: var(--gold); color: var(--dark); border: none; padding: 6px 12px; border-radius: 4px; font-weight: 600; cursor: pointer; font-size: 0.85rem; margin-top: 4px;">
              Verificar ahora
            </button>
          </p>

          <p class="auth-switch" style="margin-top: 1.5rem; text-align: center; font-size: 0.9rem; color: #aaa;">
            ¿No tienes cuenta?
            <button type="button" class="link-btn" (click)="switchToRegister()" style="background: none; border: none; color: var(--gold); font-weight: 600; cursor: pointer; margin-left: 6px; min-height: 44px; display: inline-flex; align-items: center;">
              Regístrate
            </button>
          </p>
        </form>

        <!-- REGISTER FORM -->
        <form *ngIf="mode === 'register'" (ngSubmit)="submitRegister()" class="auth-form">
          <!-- Role selector in Register -->
          <div class="auth-role-tabs" style="display: flex; gap: 6px; margin-bottom: 1.25rem; background: rgba(255,255,255,0.05); padding: 4px; border-radius: 8px; border: 1px solid rgba(201,169,110,0.25);">
            <button type="button" (click)="selectRegisterRole('user')"
                    [style.background]="registerRole === 'user' ? 'var(--gold)' : 'transparent'"
                    [style.color]="registerRole === 'user' ? '#0d0d0d' : '#f5f0e8'"
                    [style.font-weight]="registerRole === 'user' ? '700' : '500'"
                    style="flex: 1; padding: 8px 6px; border: none; border-radius: 6px; font-size: 0.82rem; cursor: pointer; transition: all 0.2s; min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 4px;">
              🛎️ Huésped
            </button>
            <button type="button" (click)="selectRegisterRole('host')"
                    [style.background]="registerRole === 'host' ? 'var(--gold)' : 'transparent'"
                    [style.color]="registerRole === 'host' ? '#0d0d0d' : '#f5f0e8'"
                    [style.font-weight]="registerRole === 'host' ? '700' : '500'"
                    style="flex: 1; padding: 8px 6px; border: none; border-radius: 6px; font-size: 0.82rem; cursor: pointer; transition: all 0.2s; min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 4px;">
              🏢 Anfitrión
            </button>
          </div>

          <div *ngIf="registerRole === 'host'" style="margin-bottom: 1rem; padding: 8px 12px; background: rgba(201,169,110,0.1); border: 1px solid rgba(201,169,110,0.3); border-radius: 6px; font-size: 0.8rem; color: #e2c99b; line-height: 1.4;">
            ✨ Publica tus alojamientos, conecta tu recepción y recibe el <strong>92% neto</strong> de cada reserva en tu cuenta bancaria.
          </div>

          <p class="modal-eyebrow" style="color: var(--gold); font-size: 0.8rem; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 4px;">{{ registerRole === 'host' ? 'Registro de Anfitrión' : 'Únete a Palacio del Mar' }}</p>
          <h2 id="reg-modal-title" class="modal-title" style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; margin: 0 0 1.5rem 0; color: #fff;">{{ registerRole === 'host' ? 'Crear cuenta de Anfitrión' : 'Crear cuenta de Huésped' }}</h2>
          
          <div style="margin-bottom: 1rem;">
            <label for="auth-reg-name" class="field-label">Nombre completo</label>
            <input id="auth-reg-name" type="text" [(ngModel)]="name" name="name" required placeholder="Tu nombre completo" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label for="auth-reg-email" class="field-label">Correo electrónico</label>
            <input id="auth-reg-email" type="email" [(ngModel)]="email" name="email" required placeholder="tu@email.com" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label for="auth-reg-phone" class="field-label">Teléfono celular</label>
            <input id="auth-reg-phone" type="tel" [(ngModel)]="phone" name="phone" required placeholder="300 123 4567" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label for="auth-reg-password" class="field-label">Contraseña</label>
            <div style="position: relative; display: flex; align-items: center;">
              <input id="auth-reg-password" [type]="showRegPassword ? 'text' : 'password'" [(ngModel)]="password" name="password" required placeholder="Mínimo 8 caracteres" style="width: 100%; padding-right: 42px;" />
              <button type="button" (click)="showRegPassword = !showRegPassword" [attr.aria-label]="showRegPassword ? 'Ocultar contraseña' : 'Ver contraseña'" style="position: absolute; right: 4px; background: none; border: none; color: var(--gold); cursor: pointer; padding: 8px; display: inline-flex; align-items: center; justify-content: center; min-height: 40px; min-width: 40px;">
                <svg *ngIf="showRegPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
                <svg *ngIf="!showRegPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                </svg>
              </button>
            </div>
          </div>

          <div style="margin-bottom: 1.5rem;">
            <label for="auth-reg-confirm" class="field-label">Confirmar contraseña</label>
            <div style="position: relative; display: flex; align-items: center;">
              <input id="auth-reg-confirm" [type]="showConfirmPassword ? 'text' : 'password'" [(ngModel)]="confirmPassword" name="confirmPassword" required placeholder="••••••••" style="width: 100%; padding-right: 42px;" />
              <button type="button" (click)="showConfirmPassword = !showConfirmPassword" [attr.aria-label]="showConfirmPassword ? 'Ocultar contraseña' : 'Ver contraseña'" style="position: absolute; right: 4px; background: none; border: none; color: var(--gold); cursor: pointer; padding: 8px; display: inline-flex; align-items: center; justify-content: center; min-height: 40px; min-width: 40px;">
                <svg *ngIf="showConfirmPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
                <svg *ngIf="!showConfirmPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                </svg>
              </button>
            </div>
          </div>

          <button type="submit" class="btn-primary" [disabled]="busy" style="width: 100%; padding: 12px; font-size: 1rem;">
            {{ busy ? 'Creando cuenta…' : 'Crear cuenta' }}
          </button>

          <p *ngIf="message" role="alert" aria-live="assertive" style="margin-top: 1rem; text-align: center; color: #ff4d4d; font-size: 0.9rem; font-weight: 500; background: rgba(255, 77, 77, 0.1); padding: 8px 12px; border-radius: 6px; border: 1px solid rgba(255, 77, 77, 0.3);">
            {{ message }}
          </p>

          <p class="auth-switch" style="margin-top: 1.5rem; text-align: center; font-size: 0.9rem; color: #aaa;">
            ¿Ya tienes cuenta?
            <button type="button" class="link-btn" (click)="switchToLogin()" style="background: none; border: none; color: var(--gold); font-weight: 600; cursor: pointer; margin-left: 6px; min-height: 44px; display: inline-flex; align-items: center;">
              Inicia sesión
            </button>
          </p>
        </form>

        <!-- FORGOT FORM -->
        <form *ngIf="mode === 'forgot'" (ngSubmit)="submitForgot()" class="auth-form">
          <p class="modal-eyebrow" style="color: var(--gold); font-size: 0.8rem; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 4px;">Recuperar acceso</p>
          <h2 id="forgot-modal-title" class="modal-title" style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; margin: 0 0 1rem 0; color: #fff;">¿Olvidaste tu contraseña?</h2>
          <p style="color: #aaa; font-size: 0.85rem; margin-bottom: 1.5rem;">Escribe tu correo y te enviaremos una contraseña temporal a tu bandeja para ingresar de inmediato.</p>

          <div style="margin-bottom: 1.5rem;">
            <label for="auth-forgot-email" class="field-label">Correo electrónico</label>
            <input id="auth-forgot-email" type="email" [(ngModel)]="email" name="email" required placeholder="tu@email.com" style="width: 100%;" />
          </div>

          <button type="submit" class="btn-primary" [disabled]="busy" style="width: 100%; padding: 12px; font-size: 1rem;">
            {{ busy ? 'Enviando contraseña…' : 'Enviar contraseña temporal' }}
          </button>

          <p *ngIf="message" [attr.role]="sentForgot ? 'status' : 'alert'" aria-live="polite" [style.color]="sentForgot ? '#2ecc71' : '#ff4d4d'" [style.background]="sentForgot ? 'rgba(46, 204, 113, 0.1)' : 'rgba(255, 77, 77, 0.1)'" [style.border-color]="sentForgot ? 'rgba(46, 204, 113, 0.3)' : 'rgba(255, 77, 77, 0.3)'" style="margin-top: 1rem; text-align: center; font-size: 0.9rem; font-weight: 500; padding: 10px 14px; border-radius: 6px; border-width: 1px; border-style: solid; display: flex; flex-direction: column; align-items: center; gap: 8px;">
            <span>{{ message }}</span>
            <button *ngIf="sentForgot" type="button" (click)="mode = 'login'" style="background: var(--gold); color: var(--dark); border: none; padding: 8px 16px; border-radius: 4px; font-weight: 600; cursor: pointer; font-size: 0.85rem; margin-top: 4px;">
              Ir a Iniciar Sesión →
            </button>
          </p>

          <p class="auth-switch" style="margin-top: 1.5rem; text-align: center; font-size: 0.9rem;">
            <button type="button" class="link-btn" (click)="mode = 'login'" style="background: none; border: none; color: var(--gold); cursor: pointer; min-height: 44px; display: inline-flex; align-items: center;">
              ← Volver a iniciar sesión
            </button>
          </p>
        </form>

      </div>
    </div>
  `
})
export class AuthModalComponent {
  private _isOpen = false;
  private _initialMode: 'login' | 'register' | 'forgot' = 'login';
  private _initialPortal: 'guest' | 'host' | 'admin' = 'guest';

  @Input() set isOpen(val: boolean) {
    this._isOpen = val;
    if (val) {
      this.mode = this._initialMode || 'login';
      this.selectedPortal = this._initialPortal || 'guest';
      this.registerRole = this._initialPortal === 'host' ? 'host' : 'user';
      this.message = '';
      this.showLoginPassword = false;
      this.showRegPassword = false;
      this.showConfirmPassword = false;
    }
  }
  get isOpen(): boolean {
    return this._isOpen;
  }

  @Input() set initialMode(mode: 'login' | 'register' | 'forgot') {
    this._initialMode = mode || 'login';
    this.mode = this._initialMode;
  }
  get initialMode(): 'login' | 'register' | 'forgot' {
    return this._initialMode;
  }

  @Output() closeEvent = new EventEmitter<void>();

  @Input() set initialPortal(p: 'guest' | 'host' | 'admin') {
    this._initialPortal = p || 'guest';
    this.selectedPortal = this._initialPortal;
    this.registerRole = this._initialPortal === 'host' ? 'host' : 'user';
  }
  get initialPortal(): 'guest' | 'host' | 'admin' {
    return this._initialPortal;
  }

  selectedPortal: 'guest' | 'host' | 'admin' = 'guest';
  registerRole: 'user' | 'host' = 'user';

  switchToRegister(): void {
    this.mode = 'register';
    this.registerRole = this.selectedPortal === 'host' ? 'host' : 'user';
    this.message = '';
  }

  switchToLogin(): void {
    this.mode = 'login';
    this.selectedPortal = this.registerRole === 'host' ? 'host' : 'guest';
    this.message = '';
  }

  selectLoginPortal(portal: 'guest' | 'host' | 'admin'): void {
    this.selectedPortal = portal;
    this.registerRole = portal === 'host' ? 'host' : 'user';
    this.message = '';
  }

  selectRegisterRole(role: 'user' | 'host'): void {
    this.registerRole = role;
    this.selectedPortal = role === 'host' ? 'host' : 'guest';
    this.message = '';
  }

  get portalEyebrow(): string {
    switch (this.selectedPortal) {
      case 'host': return 'Portal Corporativo de Anfitriones';
      case 'admin': return 'Consola Central Directiva';
      default: return 'Portal de Huéspedes';
    }
  }

  get portalTitle(): string {
    switch (this.selectedPortal) {
      case 'host': return 'Acceso Anfitrión';
      case 'admin': return 'Acceso Administrador';
      default: return 'Iniciar sesión';
    }
  }

  get portalDescription(): string {
    switch (this.selectedPortal) {
      case 'host': return 'Gestiona tus propiedades, calendarios y liquidaciones del 92%.';
      case 'admin': return 'Control central, auditoría del marketplace y operaciones.';
      default: return 'Consulta tus reservas, pagos y vouchers de acceso digital.';
    }
  }

  get portalPlaceholder(): string {
    switch (this.selectedPortal) {
      case 'host': return 'anfitrion@palaciodelmar.co';
      case 'admin': return 'admin@palaciodelmar.co';
      default: return 'tu@email.com';
    }
  }

  mode: 'login' | 'register' | 'forgot' = 'login';
  email = '';
  password = '';
  confirmPassword = '';
  name = '';
  phone = '';
  busy = false;
  message = '';
  isSuccess = false;
  sentForgot = false;
  showForceVerifyButton = false;
  showLoginPassword = false;
  showRegPassword = false;
  showConfirmPassword = false;

  constructor(
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  /**
   * L3: tras un login exitoso, si venimos de un guard que preservó returnUrl,
   * devolvemos al usuario a la página que quería ver.
   */
  private navigateToReturnUrl(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (returnUrl && returnUrl.startsWith('/')) {
      this.router.navigateByUrl(returnUrl);
    }
  }

  close(): void {
    this.isOpen = false;
    this.message = '';
    this.email = '';
    this.password = '';
    this.confirmPassword = '';
    this.name = '';
    this.phone = '';
    this.showForceVerifyButton = false;
    this.showLoginPassword = false;
    this.showRegPassword = false;
    this.showConfirmPassword = false;
    this.closeEvent.emit();
    this.cdr.detectChanges();
  }

  submitLogin(): void {
    this.busy = true;
    this.message = '';
    this.isSuccess = false;
    this.showForceVerifyButton = false;
    this.cdr.detectChanges();

    const payload = {
      email: (this.email || '').trim(),
      password: this.password
    };

    this.auth.login(payload).subscribe({
      next: (res) => {
        const role = (res?.user?.role || this.auth.currentUser()?.role || '').toLowerCase();

        if (this.selectedPortal === 'admin' && role !== 'admin') {
          this.busy = false;
          this.message = 'Esta cuenta tiene rol de ' + (role === 'host' ? 'Anfitrión' : 'Huésped') + ', no de Administrador. Selecciona la pestaña adecuada.';
          this.isSuccess = false;
          this.auth.logout();
          this.cdr.detectChanges();
          return;
        }

        if (this.selectedPortal === 'host' && role !== 'host' && role !== 'anfitrion') {
          this.busy = false;
          this.message = 'Esta cuenta tiene rol de ' + (role === 'admin' ? 'Administrador' : 'Huésped') + ', no de Anfitrión. Selecciona la pestaña Huésped.';
          this.isSuccess = false;
          this.auth.logout();
          this.cdr.detectChanges();
          return;
        }

        this.busy = false;
        this.close();
        if (role === 'admin') {
          this.router.navigate(['/admin']);
        } else if (role === 'host' || role === 'anfitrion') {
          this.router.navigate(['/anfitrion']);
        } else {
          this.navigateToReturnUrl();
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.busy = false;
        this.isSuccess = false;
        this.message = err.error?.message || 'Credenciales inválidas.';
        // Si el usuario no ha verificado su correo, mostrar botón para verificar con 1 clic
        if (this.message.includes('verificar tu correo electrónico') || this.message.toLowerCase().includes('verificar')) {
          this.showForceVerifyButton = true;
        }
        this.cdr.detectChanges();
      }
    });
  }

  forceVerify(): void {
    if (!this.email) return;
    this.busy = true;
    this.auth.forceVerify(this.email.trim()).subscribe({
      next: () => {
        this.busy = false;
        this.isSuccess = true;
        this.showForceVerifyButton = false;
        this.message = '¡Correo verificado! Iniciando sesión...';
        this.cdr.detectChanges();
        // Automatically submit login again after verification
        setTimeout(() => this.submitLogin(), 1000);
      },
      error: () => {
        this.busy = false;
        this.isSuccess = false;
        this.message = 'No se pudo verificar. Intenta de nuevo.';
        this.cdr.detectChanges();
      }
    });
  }

  submitRegister(): void {
    if (this.password !== this.confirmPassword) {
      this.isSuccess = false;
      this.message = 'Las contraseñas no coinciden.';
      return;
    }
    if (this.password.length < 8) {
      this.isSuccess = false;
      this.message = 'La contraseña debe tener al menos 8 caracteres.';
      return;
    }

    this.busy = true;
    this.message = '';
    this.isSuccess = false;
    this.cdr.detectChanges();

    const payload = {
      email: (this.email || '').trim(),
      password: this.password,
      name: (this.name || '').trim(),
      phone: (this.phone || '').trim(),
      role: this.registerRole
    };

    this.auth.register(payload).subscribe({
      next: () => {
        this.busy = false;
        this.selectedPortal = this.registerRole === 'host' ? 'host' : 'guest';
        this.mode = 'login';
        this.isSuccess = true;
        this.message = '¡Cuenta creada! Te hemos enviado un enlace a tu correo. Por favor verifícalo para iniciar sesión.';
        this.password = '';
        this.confirmPassword = '';
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.busy = false;
        this.isSuccess = false;
        const rawMsg = err.error?.message || '';
        const lowerMsg = rawMsg.toLowerCase();
        if (lowerMsg.includes('teléfono') || lowerMsg.includes('telefono') || lowerMsg.includes('phone')) {
          this.message = rawMsg || 'El número de teléfono ya está registrado con otra cuenta.';
        } else if (lowerMsg.includes('email') || lowerMsg.includes('correo')) {
          this.message = `El correo ${payload.email} ya está registrado. Por favor haz clic en "Inicia sesión".`;
        } else {
          this.message = rawMsg || 'No se pudo crear la cuenta. Intenta de nuevo.';
        }
        this.cdr.detectChanges();
      }
    });
  }

  submitForgot(): void {
    if (!this.email || !this.email.trim()) {
      this.sentForgot = false;
      this.message = 'Por favor escribe tu correo electrónico.';
      this.cdr.detectChanges();
      return;
    }
    this.busy = true;
    this.message = '';
    this.sentForgot = false;
    this.cdr.detectChanges();

    this.auth.forgotPassword(this.email.trim()).subscribe({
      next: (res: any) => {
        this.busy = false;
        this.sentForgot = true;
        this.message = res?.message || 'Si tu correo se encuentra registrado, te hemos enviado una contraseña temporal para ingresar. Revisa tu bandeja de entrada o spam.';
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        this.busy = false;
        this.sentForgot = false;
        this.message = err?.error?.message || 'No se pudo procesar la solicitud. Intenta de nuevo más tarde.';
        this.cdr.detectChanges();
      }
    });
  }
}


