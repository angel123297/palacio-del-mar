import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
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

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}

  ngOnInit() {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.status.set('error');
      this.message.set('Error: Enlace inválido o expirado');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next: (res) => {
        this.status.set('success');
        this.message.set('Éxito: Tu correo ha sido verificado. Ya puedes disfrutar de la experiencia Palacio del Mar.');
      },
      error: (err) => {
        this.status.set('error');
        this.message.set('Error: Enlace inválido o expirado');
      }
    });
  }
}
