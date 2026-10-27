import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  newPassword = '';
  confirmPassword = '';
  showPassword = signal(false);
  isSubmitting = signal(false);
  errorMessage = signal('');
  message = signal('');
  status = signal<'form' | 'loading' | 'success' | 'error'>('form');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    // Buscar token en params de ruta (/restablecer-contrasena/:token) o queryParams (?token=...)
    const routeToken = this.route.snapshot.paramMap.get('token');
    const queryToken = this.route.snapshot.queryParamMap.get('token');
    this.token = (routeToken || queryToken || '').trim();

    if (!this.token) {
      this.status.set('error');
      this.message.set('Enlace incompleto o inválido: no se proporcionó el código de seguridad.');
    }
  }

  toggleShowPassword(): void {
    this.showPassword.update(v => !v);
  }

  submitReset(): void {
    this.errorMessage.set('');

    if (!this.token) {
      this.errorMessage.set('Token de restablecimiento no encontrado.');
      return;
    }

    if (!this.newPassword || this.newPassword.length < 6) {
      this.errorMessage.set('La nueva contraseña debe tener al menos 6 caracteres.');
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage.set('Las contraseñas no coinciden. Por favor verifica.');
      return;
    }

    this.isSubmitting.set(true);

    this.authService.resetPassword(this.token, this.newPassword).subscribe({
      next: (res) => {
        this.isSubmitting.set(false);
        this.status.set('success');
        this.message.set(res?.message || 'Tu contraseña ha sido actualizada con éxito.');
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const errMsg = err?.error?.message || 'El enlace ha expirado o no es válido. Solicita uno nuevo.';
        this.errorMessage.set(errMsg);
      }
    });
  }
}
