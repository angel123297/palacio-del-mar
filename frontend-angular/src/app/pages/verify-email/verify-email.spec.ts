import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VerifyEmail } from './verify-email';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { of } from 'rxjs';
import { vi } from 'vitest';

describe('VerifyEmail', () => {
  let component: VerifyEmail;
  let fixture: ComponentFixture<VerifyEmail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifyEmail],
      providers: [provideHttpClient(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(VerifyEmail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('abre la aplicación tras verificar una cuenta originada en Flutter', () => {
    vi.useFakeTimers();
    const route = { snapshot: { queryParamMap: { get: () => '/app/' }, paramMap: { get: () => 'verification-token' } } } as unknown as ActivatedRoute;
    const router = { navigateByUrl: vi.fn() } as unknown as Router;
    const auth = { verifyEmail: vi.fn(() => of({ user: { role: 'user' } })) } as unknown as AuthService;
    const page = new VerifyEmail(route, router, auth);
    const redirect = vi.spyOn(page, 'redirectToApplication').mockImplementation(() => {});
    page.ngOnInit();
    vi.advanceTimersByTime(2000);
    expect(redirect).toHaveBeenCalledOnce();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    page.ngOnDestroy();
    vi.useRealTimers();
  });
});
