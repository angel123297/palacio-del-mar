import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div *ngIf="isOpen" style="position: fixed; inset: 0; background: rgba(0,0,0,0.75); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 1rem;" (click)="close()">
      <div style="background: #121212; border: 1px solid rgba(212,175,55,0.3); border-radius: 12px; padding: 2rem; width: 100%; max-width: 420px; color: #ffffff;" (click)="$event.stopPropagation()">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 1rem;">
          <h2 style="margin: 0; font-family: var(--font-title); color: var(--gold); font-size: 1.5rem;">
            {{ isRegister ? 'Crear Cuenta' : 'Iniciar Sesión' }}
          </h2>
          <button (click)="close()" style="background: none; border: none; color: #aaa; font-size: 1.5rem; cursor: pointer;">×</button>
        </div>

        <form (ngSubmit)="submit()">
          <div *ngIf="isRegister" style="margin-bottom: 1rem;">
            <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 4px;">Nombre completo</label>
            <input type="text" [(ngModel)]="name" name="name" required placeholder="Ej: Maria Gomez" style="width: 100%; padding: 10px; background: rgba(255,255,255,0.05); border: 1px solid rgba(212,175,55,0.3); border-radius: 6px; color: #fff;">
          </div>

          <div style="margin-bottom: 1rem;">
            <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 4px;">Correo electrónico</label>
            <input type="email" [(ngModel)]="email" name="email" required placeholder="ejemplo@correo.com" style="width: 100%; padding: 10px; background: rgba(255,255,255,0.05); border: 1px solid rgba(212,175,55,0.3); border-radius: 6px; color: #fff;">
          </div>

          <div style="margin-bottom: 1.5rem;">
            <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 4px;">Contraseña</label>
            <input type="password" [(ngModel)]="password" name="password" required placeholder="••••••••" style="width: 100%; padding: 10px; background: rgba(255,255,255,0.05); border: 1px solid rgba(212,175,55,0.3); border-radius: 6px; color: #fff;">
          </div>

          <button type="submit" [disabled]="busy" style="width: 100%; background: var(--gold); color: #0d0d0d; font-weight: 700; padding: 12px; border: none; border-radius: 6px; font-size: 1rem; cursor: pointer;">
            {{ busy ? 'Procesando…' : (isRegister ? 'Registrarme' : 'Entrar') }}
          </button>

          <p *ngIf="message" style="margin-top: 1rem; text-align: center; color: #ff4d4d; font-size: 0.85rem;">{{message}}</p>

          <div style="margin-top: 1.5rem; text-align: center; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 1rem;">
            <button type="button" (click)="toggleMode()" style="background: none; border: none; color: var(--gold); cursor: pointer; font-size: 0.9rem;">
              {{ isRegister ? '¿Ya tienes cuenta? Inicia sesión' : '¿No tienes cuenta? Regístrate aquí' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class AuthModalComponent {
  @Input() isOpen = false;
  @Output() closeEvent = new EventEmitter<void>();

  isRegister = false;
  email = '';
  password = '';
  name = '';
  busy = false;
  message = '';

  constructor(private auth: AuthService) {}

  toggleMode(): void {
    this.isRegister = !this.isRegister;
    this.message = '';
  }

  close(): void {
    this.isOpen = false;
    this.closeEvent.emit();
  }

  submit(): void {
    this.busy = true;
    this.message = '';

    if (this.isRegister) {
      this.auth.register({ email: this.email, password: this.password, name: this.name }).subscribe({
        next: () => {
          this.busy = false;
          this.close();
        },
        error: (err) => {
          this.busy = false;
          this.message = err.error?.message || 'Error en el registro.';
        }
      });
    } else {
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
  }
}
