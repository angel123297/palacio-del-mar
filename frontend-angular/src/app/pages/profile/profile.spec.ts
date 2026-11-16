import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileComponent } from './profile';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let apiServiceMock: any;
  let authServiceMock: any;
  let routerMock: any;

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockReturnValue(of({ success: true, data: { user: { name: 'Juan Perez', email: 'juan@palacio.co' } } })),
      put: vi.fn().mockReturnValue(of({ success: true })),
      post: vi.fn().mockReturnValue(of({ success: true }))
    };

    authServiceMock = {
      currentUser: signal({ id: 'u1', name: 'Juan Perez', email: 'juan@palacio.co', role: 'guest' }),
      updateCurrentUser: vi.fn(),
      clearLocalData: vi.fn(),
      logoutAllDevices: vi.fn().mockReturnValue(of({ success: true, message: 'Sesiones revocadas' }))
    };

    routerMock = {
      navigate: vi.fn().mockResolvedValue(true)
    };

    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: ApiService, useValue: apiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
  });

  it('ngOnInit muestra error y limpia sesion local cuando /auth/me responde 401', () => {
    apiServiceMock.get.mockReturnValue(throwError(() => ({ status: 401 })));

    component.ngOnInit();

    expect(component.profileError).toBe('Sesión expirada o no autorizada. Por favor inicia sesión nuevamente.');
    expect(authServiceMock.clearLocalData).toHaveBeenCalled();
  });

  it('changePassword valida que las contraseñas coincidan antes de llamar a la API', () => {
    component.passwordForm.setValue({
      currentPassword: 'Password123!',
      newPassword: 'NewSecurePassword1!',
      confirmPassword: 'DifferentPassword2!'
    });

    component.changePassword();

    expect(component.passwordError).toBe('Las contraseñas no coinciden.');
    expect(apiServiceMock.put).not.toHaveBeenCalledWith('/auth/change-password', expect.anything());

    component.passwordForm.patchValue({
      confirmPassword: 'NewSecurePassword1!'
    });
    component.changePassword();

    expect(component.passwordError).toBe('');
    expect(apiServiceMock.put).toHaveBeenCalledWith('/auth/change-password', {
      currentPassword: 'Password123!',
      newPassword: 'NewSecurePassword1!'
    });
  });
});
