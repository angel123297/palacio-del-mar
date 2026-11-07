import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResetPasswordComponent } from './reset-password.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { of, throwError } from 'rxjs';

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let authServiceMock: { resetPassword: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceMock = {
      resetPassword: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: (key: string) => (key === 'token' ? 'valid-token-123' : null) },
              queryParamMap: { get: () => null }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente e inicializar el token de la ruta', () => {
    expect(component).toBeTruthy();
    expect(component.token).toBe('valid-token-123');
    expect(component.status()).toBe('form');
  });

  it('debe mostrar error si las contraseñas no coinciden', () => {
    component.newPassword = 'Password123';
    component.confirmPassword = 'DifferentPassword456';
    component.submitReset();

    expect(component.errorMessage()).toContain('no coinciden');
    expect(authServiceMock.resetPassword).not.toHaveBeenCalled();
  });

  it('debe mostrar error si la contraseña tiene menos de 6 caracteres', () => {
    component.newPassword = '123';
    component.confirmPassword = '123';
    component.submitReset();

    expect(component.errorMessage()).toContain('al menos 6 caracteres');
    expect(authServiceMock.resetPassword).not.toHaveBeenCalled();
  });

  it('debe cambiar de estado a success cuando el reseteo es exitoso', () => {
    authServiceMock.resetPassword.mockReturnValue(of({ success: true, message: 'Éxito' }));

    component.newPassword = 'ValidPassword123';
    component.confirmPassword = 'ValidPassword123';
    component.submitReset();

    expect(authServiceMock.resetPassword).toHaveBeenCalledWith('valid-token-123', 'ValidPassword123');
    expect(component.status()).toBe('success');
  });

  it('debe mostrar error devuelto por la API si el enlace expiró', () => {
    authServiceMock.resetPassword.mockReturnValue(
      throwError(() => ({ error: { message: 'El enlace de restablecimiento ha expirado.' } }))
    );

    component.newPassword = 'ValidPassword123';
    component.confirmPassword = 'ValidPassword123';
    component.submitReset();

    expect(component.errorMessage()).toBe('El enlace de restablecimiento ha expirado.');
    expect(component.status()).toBe('form');
  });
});
