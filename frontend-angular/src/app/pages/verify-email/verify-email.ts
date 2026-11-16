import { Component, OnInit, OnDestroy, signal } from '@angular/core';
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
export class VerifyEmail implements OnInit, OnDestroy {
  status = signal<'loading' | 'success' | 'error'>('loading');
  message = signal<string>('Verificando tu correo electrónico...');
  returnUrl = signal<string>('/');
  private redirectTimer: any = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit() {
    const rawReturn = this.route.snapshot.queryParamMap.get('returnUrl');
    if (rawReturn && rawReturn.startsWith('/') && !rawReturn.startsWith('//')) {
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
        const role = (res?.user?.role || '').toLowerCase();
        let targetUrl = this.returnUrl();
        if (targetUrl === '/') {
          if (role === 'admin') {
            targetUrl = '/admin';
          } else if (role === 'host' || role === 'anfitrion') {
            targetUrl = '/anfitrion';
          }
        }
        this.redirectTimer = setTimeout(() => {
          this.router.navigateByUrl(targetUrl);
        }, 2000);
      },
      error: (err) => {
        this.status.set('error');
        this.message.set(err?.error?.message || err?.message || 'Error: Enlace inválido o expirado');
      }
    });
  }

  ngOnDestroy() {
    if (this.redirectTimer) {
      clearTimeout(this.redirectTimer);
      this.redirectTimer = null;
    }
  }
}
