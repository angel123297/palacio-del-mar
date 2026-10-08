import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div className="modal-backdrop" *ngIf="isOpen" (click)="close()" style="position: fixed; inset: 0; background: rgba(0,0,0,0.85); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 1rem;">
      <div className="modal-content" (click)="$event.stopPropagation()" style="background: var(--dark); border: 1px solid var(--gold); border-radius: 12px; padding: 2rem; width: 100%; max-width: 440px; color: var(--cream); position: relative;">
        
        <button (click)="close()" style="position: absolute; top: 16px; right: 16px; background: none; border: none; color: #aaa; font-size: 1.5rem; cursor: pointer;">×</button>

        <!-- LOGIN FORM -->
        <form *ngIf="mode === 'login'" (ngSubmit)="submitLogin()" class="auth-form">
          <p className="modal-eyebrow" style="color: var(--gold); font-size: 0.8rem; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 4px;">Bienvenido de nuevo</p>
          <h2 className="modal-title" style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; margin: 0 0 1.5rem 0; color: #fff;">Iniciar sesión</h2>
          
          <div style="margin-bottom: 1rem;">
            <label className="field-label">Correo electrónico</label>
            <input type="email" [(ngModel)]="email" name="email" required placeholder="tu@email.com" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label className="field-label">Contraseña</label>
            <input type="password" [(ngModel)]="password" name="password" required placeholder="••••••••" style="width: 100%;" />
          </div>

          <div style="text-align: right; margin-bottom: 1rem;">
            <button type="button" className="link-btn" (click)="mode = 'forgot'" style="background: none; border: none; color: var(--gold); font-size: 0.85rem; cursor: pointer;">
              ¿Olvidaste tu contraseña?
            </button>
          </div>

          <button type="submit" className="btn-primary" [disabled]="busy" style="width: 100%; padding: 12px; font-size: 1rem;">
            {{ busy ? 'Entrando…' : 'Iniciar sesión' }}
          </button>

          <p *ngIf="message" style="margin-top: 1rem; text-align: center; color: #ff4d4d; font-size: 0.85rem;">{{ message }}</p>

          <p className="auth-switch" style="margin-top: 1.5rem; text-align: center; font-size: 0.9rem; color: #aaa;">
            ¿No tienes cuenta?
            <button type="button" className="link-btn" (click)="mode = 'register'" style="background: none; border: none; color: var(--gold); font-weight: 600; cursor: pointer; margin-left: 6px;">
              Regístrate
            </button>
          </p>
        </form>

        <!-- REGISTER FORM -->
        <form *ngIf="mode === 'register'" (ngSubmit)="submitRegister()" class="auth-form">
          <p className="modal-eyebrow" style="color: var(--gold); font-size: 0.8rem; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 4px;">Únete a Palacio del Mar</p>
          <h2 className="modal-title" style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; margin: 0 0 1.5rem 0; color: #fff;">Crear cuenta</h2>
          
          <div style="margin-bottom: 1rem;">
            <label className="field-label">Nombre completo</label>
            <input type="text" [(ngModel)]="name" name="name" required placeholder="Tu nombre completo" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label className="field-label">Correo electrónico</label>
            <input type="email" [(ngModel)]="email" name="email" required placeholder="tu@email.com" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label className="field-label">Teléfono celular</label>
            <input type="tel" [(ngModel)]="phone" name="phone" required placeholder="300 123 4567" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1rem;">
            <label className="field-label">Contraseña</label>
            <input type="password" [(ngModel)]="password" name="password" required placeholder="Mínimo 8 caracteres" style="width: 100%;" />
          </div>

          <div style="margin-bottom: 1.5rem;">
            <label className="field-label">Confirmar contraseña</label>
            <input type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required placeholder="••••••••" style="width: 100%;" />
          </div>

          <button type="submit" className="btn-primary" [disabled]="busy" style="width: 100%; padding: 12px; font-size: 1rem;">
            {{ busy ? 'Creando cuenta…' : 'Crear cuenta' }}
          </button>

          <p *ngIf="message" style="margin-top: 1rem; text-align: center; color: #ff4d4d; font-size: 0.85rem;">{{ message }}</p>

          <p className="auth-switch" style="margin-top: 1.5rem; text-align: center; font-size: 0.9rem; color: #aaa;">
            ¿Ya tienes cuenta?
            <button type="button" className="link-btn" (click)="mode = 'login'" style="background: none; border: none; color: var(--gold); font-weight: 600; cursor: pointer; margin-left: 6px;">
              Inicia sesión
            </button>
          </p>
        </form>

        <!-- FORGOT FORM -->
        <form *ngIf="mode === 'forgot'" (ngSubmit)="submitForgot()" class="auth-form">
          <p className="modal-eyebrow" style="color: var(--gold); font-size: 0.8rem; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 4px;">Recuperar acceso</p>
          <h2 className="modal-title" style="font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; margin: 0 0 1rem 0; color: #fff;">¿Olvidaste tu contraseña?</h2>
          <p style="color: #aaa; font-size: 0.85rem; margin-bottom: 1.5rem;">Escribe tu correo y te enviaremos las instrucciones para restablecerla.</p>

          <div style="margin-bottom: 1.5rem;">
            <label className="field-label">Correo electrónico</label>
            <input type="email" [(ngModel)]="email" name="email" required placeholder="tu@email.com" style="width: 100%;" />
          </div>

          <button type="submit" className="btn-primary" [disabled]="busy" style="width: 100%; padding: 12px; font-size: 1rem;">
            {{ busy ? 'Enviando…' : 'Enviar enlace de recuperación' }}
          </button>

          <p *ngIf="message" style="margin-top: 1rem; text-align: center; font-size: 0.85rem;" [style.color]="sentForgot ? '#6fcf97' : '#ff4d4d'">{{ message }}</p>

          <p className="auth-switch" style="margin-top: 1.5rem; text-align: center; font-size: 0.9rem;">
            <button type="button" className="link-btn" (click)="mode = 'login'" style="background: none; border: none; color: var(--gold); cursor: pointer;">
              ← Volver a iniciar sesión
            </button>
          </p>
        </form>

      </div>
    </div>
  `
})
export class AuthModalComponent {
  @Input() isOpen = false;
  @Input() set initialMode(mode: 'login' | 'register' | 'forgot') {
    this.mode = mode || 'login';
  }
  @Output() closeEvent = new EventEmitter<void>();

  mode: 'login' | 'register' | 'forgot' = 'login';
  email = '';
  password = '';
  confirmPassword = '';
  name = '';
  phone = '';
  busy = false;
  message = '';
  sentForgot = false;

  constructor(private auth: AuthService) {}

  close(): void {
    this.isOpen = false;
    this.closeEvent.emit();
  }

  submitLogin(): void {
    this.busy = true;
    this.message = '';

    this.auth.login({ email: this.email, password: this.password }).subscribe({
      next: () => {
        this.busy = false;
        this.close();
      },
      error: (err) => {
        this.busy = false;
        this.message = err.error?.message || 'Credenciales inválidas.';
      }
    });
  }

  submitRegister(): void {
    if (this.password !== this.confirmPassword) {
      this.message = 'Las contraseñas no coinciden.';
      return;
    }
    if (this.password.length < 8) {
      this.message = 'La contraseña debe tener al menos 8 caracteres.';
      return;
    }

    this.busy = true;
    this.message = '';

    this.auth.register({ email: this.email, password: this.password, name: this.name, phone: this.phone }).subscribe({
      next: () => {
        this.busy = false;
        this.close();
      },
      error: (err) => {
        this.busy = false;
        this.message = err.error?.message || 'No se pudo crear la cuenta.';
      }
    });
  }

  submitForgot(): void {
    this.busy = true;
    this.message = '';
    this.sentForgot = false;

    setTimeout(() => {
      this.busy = false;
      this.sentForgot = true;
      this.message = 'Si el correo está registrado, te llegará un enlace para restablecer tu contraseña.';
    }, 600);
  }
}
