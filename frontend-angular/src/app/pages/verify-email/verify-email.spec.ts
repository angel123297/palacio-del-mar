import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VerifyEmail } from './verify-email';
import { ActivatedRoute } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of } from 'rxjs';

describe('VerifyEmail', () => {
  let component: VerifyEmail;
  let fixture: ComponentFixture<VerifyEmail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifyEmail, HttpClientTestingModule],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => 'some-token',
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(VerifyEmail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
