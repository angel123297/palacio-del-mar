import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.scss',
})
export class VerifyEmail implements OnInit {
  status = signal<'loading' | 'success' | 'error'>('loading');
  message = signal<string>('Verificando tu correo electrónico...');
  returnUrl = signal<string>('/');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit() {
    const rawReturn = this.route.snapshot.queryParamMap.get('returnUrl');
    if (rawReturn && rawReturn.startsWith('/')) {
      this.returnUrl.set(rawReturn);
    }

    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.status.set('error');
      this.message.set('Error: Enlace inválido o expirado');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next: (res) => {
        this.status.set('success');
        this.message.set('¡Tu correo ha sido verificado con éxito! Hemos iniciado tu sesión automáticamente.');
        setTimeout(() => {
          this.router.navigateByUrl(this.returnUrl());
        }, 2000);
      },
      error: (err) => {
        this.status.set('error');
        this.message.set('Error: Enlace inválido o expirado');
      }
    });
  }
}
