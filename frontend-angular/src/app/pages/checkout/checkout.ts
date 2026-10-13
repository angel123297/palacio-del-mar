import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking } from '../../models/types';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 4rem 1.5rem; display: flex; justify-content: center;">
      <div style="max-width: 1100px; width: 100%; display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 3rem;">
        
        <!-- Left: Payment Methods -->
        <div>
          <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); margin-bottom: 2rem;">Centro de Pago Seguro</h1>
          
          <div *ngIf="loading" class="luxury-loader-container" style="min-height: 200px;">
            <div class="luxury-spinner"></div>
            <p class="luxury-loader-text">Cargando detalles de tu reserva...</p>
          </div>

          <div *ngIf="!loading && !booking" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem; text-align: center;">
            <p style="color: #ff4d4d; margin-bottom: 1rem; font-size: 1.1rem;">No se encontró la reserva o ya ha sido pagada previamente.</p>
            <a routerLink="/mis-reservas" class="btn-primary" style="text-decoration: none; padding: 10px 20px;">Volver a mis reservas</a>
          </div>

          <div *ngIf="!loading && booking" style="background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem;">
            
            <!-- 15-Minute Guaranteed Reservation TTL Countdown -->
            <div style="background: rgba(201,169,110,0.1); border: 1px solid rgba(201,169,110,0.4); border-radius: 8px; padding: 12px 18px; margin-bottom: 1.5rem; display: flex; justify-content: space-between; align-items: center;">
              <span style="font-size: 0.9rem; color: #f5f0e8;">⏱️ <strong>Tiempo restante para pagar tu reserva:</strong></span>
              <span style="font-family: monospace; font-size: 1.2rem; font-weight: bold; color: var(--gold); letter-spacing: 2px;">{{ timerText }}</span>
            </div>

            <!-- Partial / Instalment Payment Plan Selection -->
            <div *ngIf="balanceDue > 0" style="margin-bottom: 1.5rem; background: rgba(0,0,0,0.4); border: 1px solid rgba(201,169,110,0.3); border-radius: 8px; padding: 1.2rem;">
              <label style="display: block; color: var(--gold); font-size: 0.85rem; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 0.8rem; font-weight: 600;">Plan de Pago</label>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                <button type="button" (click)="paymentMode = 'full'" 
                  [style.border-color]="paymentMode === 'full' ? 'var(--gold)' : 'rgba(255,255,255,0.15)'"
                  [style.background]="paymentMode === 'full' ? 'rgba(201,169,110,0.15)' : 'rgba(255,255,255,0.02)'"
                  style="padding: 12px; border-width: 2px; border-style: solid; border-radius: 8px; color: #fff; cursor: pointer; text-align: left; transition: all 0.2s;">
                  <div style="font-weight: 600; font-size: 0.95rem; margin-bottom: 4px;">Pago Total</div>
                  <div style="color: var(--gold); font-size: 1.1rem; font-weight: 700;">$ {{ balanceDue | number:'1.0-0' }}</div>
                </button>
                <button type="button" (click)="paymentMode = 'partial'" 
                  [style.border-color]="paymentMode === 'partial' ? 'var(--gold)' : 'rgba(255,255,255,0.15)'"
                  [style.background]="paymentMode === 'partial' ? 'rgba(201,169,110,0.15)' : 'rgba(255,255,255,0.02)'"
                  style="padding: 12px; border-width: 2px; border-style: solid; border-radius: 8px; color: #fff; cursor: pointer; text-align: left; transition: all 0.2s;">
                  <div style="font-weight: 600; font-size: 0.95rem; margin-bottom: 4px;">Anticipo 50%</div>
                  <div style="color: var(--gold); font-size: 1.1rem; font-weight: 700;">$ {{ Math.round(balanceDue / 2) | number:'1.0-0' }}</div>
                </button>
              </div>
            </div>

            <h3 style="color: #fff; margin-bottom: 1.5rem; font-size: 1.3rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 0.5rem;">Elige tu método de pago</h3>

            <!-- Payment Method Selector -->
            <div style="display: flex; gap: 1rem; margin-bottom: 2rem; overflow-x: auto; padding-bottom: 0.5rem;">
              <button type="button" (click)="selectedMethod = 'CARD'" 
                [style.border-color]="selectedMethod === 'CARD' ? 'var(--gold)' : 'rgba(255,255,255,0.2)'"
                [style.background]="selectedMethod === 'CARD' ? 'rgba(212,175,55,0.1)' : 'transparent'"
                style="flex: 1; min-width: 140px; padding: 1.5rem 1rem; border-width: 2px; border-style: solid; border-radius: 12px; color: #fff; cursor: pointer; transition: all 0.3s; display: flex; flex-direction: column; align-items: center; gap: 10px;">
                <span style="font-size: 2rem;">💳</span>
                <span style="font-weight: 600;">Tarjeta de Crédito</span>
              </button>

              <button type="button" (click)="selectedMethod = 'PSE'"
                [style.border-color]="selectedMethod === 'PSE' ? 'var(--gold)' : 'rgba(255,255,255,0.2)'"
                [style.background]="selectedMethod === 'PSE' ? 'rgba(212,175,55,0.1)' : 'transparent'"
                style="flex: 1; min-width: 140px; padding: 1.5rem 1rem; border-width: 2px; border-style: solid; border-radius: 12px; color: #fff; cursor: pointer; transition: all 0.3s; display: flex; flex-direction: column; align-items: center; gap: 10px;">
                <span style="font-size: 2rem;">🏦</span>
                <span style="font-weight: 600;">PSE / Bancos</span>
              </button>

              <button type="button" (click)="selectedMethod = 'PAYPAL'"
                [style.border-color]="selectedMethod === 'PAYPAL' ? 'var(--gold)' : 'rgba(255,255,255,0.2)'"
                [style.background]="selectedMethod === 'PAYPAL' ? 'rgba(212,175,55,0.1)' : 'transparent'"
                style="flex: 1; min-width: 140px; padding: 1.5rem 1rem; border-width: 2px; border-style: solid; border-radius: 12px; color: #fff; cursor: pointer; transition: all 0.3s; display: flex; flex-direction: column; align-items: center; gap: 10px;">
                <span style="font-size: 2rem;">🅿️</span>
                <span style="font-weight: 600;">PayPal</span>
              </button>
            </div>

            <!-- CREDIT CARD FORM -->
            <form *ngIf="selectedMethod === 'CARD'" [formGroup]="paymentForm" (ngSubmit)="processPayment()">
              <div style="background: rgba(0,0,0,0.3); padding: 1.5rem; border-radius: 8px; border: 1px solid rgba(255,255,255,0.05); margin-bottom: 2rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;">
                  <h4 style="margin: 0; color: #ccc;">Detalles de la Tarjeta</h4>
                  <div style="display: flex; gap: 8px;">
                    <div style="width: 35px; height: 22px; background: #fff; border-radius: 4px; display: flex; align-items: center; justify-content: center; color: #000; font-weight: bold; font-size: 0.6rem; font-style: italic;">VISA</div>
                    <div style="width: 35px; height: 22px; background: #fff; border-radius: 4px; display: flex; align-items: center; justify-content: center; color: #eb001b; font-weight: bold; font-size: 0.6rem;">MC</div>
                  </div>
                </div>

                <div style="margin-bottom: 1.2rem;">
                  <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Titular de la tarjeta</label>
                  <input type="text" formControlName="cardName" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;" [style.border-color]="paymentForm.get('cardName')?.invalid && paymentForm.get('cardName')?.touched ? '#ff4d4d' : 'rgba(255,255,255,0.1)'" placeholder="Como aparece en la tarjeta">
                  <div *ngIf="paymentForm.get('cardName')?.invalid && paymentForm.get('cardName')?.touched" style="color: #ff4d4d; font-size: 0.8rem; margin-top: 5px;">Campo requerido</div>
                </div>

                <div style="margin-bottom: 1.2rem;">
                  <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Número de Tarjeta</label>
                  <input type="text" formControlName="cardNumber" maxlength="19" (input)="formatCardNumber($event)" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem; letter-spacing: 2px;" [style.border-color]="paymentForm.get('cardNumber')?.invalid && paymentForm.get('cardNumber')?.touched ? '#ff4d4d' : 'rgba(255,255,255,0.1)'" placeholder="0000 0000 0000 0000">
                  <div *ngIf="paymentForm.get('cardNumber')?.invalid && paymentForm.get('cardNumber')?.touched" style="color: #ff4d4d; font-size: 0.8rem; margin-top: 5px;">Número inválido</div>
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.2rem;">
                  <div>
                    <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Expiración</label>
                    <input type="text" formControlName="expiry" maxlength="5" (input)="formatExpiry($event)" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem; text-align: center;" [style.border-color]="paymentForm.get('expiry')?.invalid && paymentForm.get('expiry')?.touched ? '#ff4d4d' : 'rgba(255,255,255,0.1)'" placeholder="MM/AA">
                    <div *ngIf="paymentForm.get('expiry')?.invalid && paymentForm.get('expiry')?.touched" style="color: #ff4d4d; font-size: 0.8rem; margin-top: 5px;">Formato MM/AA</div>
                  </div>
                  <div>
                    <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">CVV / CVC</label>
                    <input type="password" formControlName="cvv" maxlength="4" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem; text-align: center; letter-spacing: 3px;" [style.border-color]="paymentForm.get('cvv')?.invalid && paymentForm.get('cvv')?.touched ? '#ff4d4d' : 'rgba(255,255,255,0.1)'" placeholder="•••">
                    <div *ngIf="paymentForm.get('cvv')?.invalid && paymentForm.get('cvv')?.touched" style="color: #ff4d4d; font-size: 0.8rem; margin-top: 5px;">CVV inválido</div>
                  </div>
                </div>
              </div>

              <button type="submit" [disabled]="processing" class="btn-primary" style="width: 100%; padding: 16px; font-size: 1.1rem; font-weight: 700; text-transform: uppercase; letter-spacing: 1px;">
                <span *ngIf="!processing">Pagar $ {{ paymentAmount | number:'1.0-0' }}</span>
                <span *ngIf="processing" style="display: flex; align-items: center; justify-content: center; gap: 10px;">
                  Autorizando transacción... <div class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
                </span>
              </button>
            </form>

            <!-- PSE FORM -->
            <div *ngIf="selectedMethod === 'PSE'">
              <div style="background: rgba(0,0,0,0.3); padding: 2rem; border-radius: 8px; border: 1px solid rgba(255,255,255,0.05); margin-bottom: 2rem; text-align: center;">
                <h4 style="margin: 0 0 1.5rem 0; color: #ccc;">Pago Seguro en Línea (PSE)</h4>
                
                <div style="text-align: left; margin-bottom: 1.5rem;">
                  <label style="display: block; color: #aaa; margin-bottom: 0.5rem; font-size: 0.9rem;">Selecciona tu banco</label>
                  <select [(ngModel)]="pseBank" style="width: 100%; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: 12px; border-radius: 6px; font-size: 1rem;">
                    <option value="">-- Elige un banco --</option>
                    <option value="bancolombia">Bancolombia</option>
                    <option value="davivienda">Davivienda</option>
                    <option value="bogota">Banco de Bogotá</option>
                    <option value="bbva">BBVA Colombia</option>
                    <option value="occidente">Banco de Occidente</option>
                    <option value="nequi">Nequi</option>
                    <option value="daviplata">DaviPlata</option>
                  </select>
                </div>
                <p style="font-size: 0.85rem; color: #888;">Al continuar, serás redirigido a la pasarela segura de tu banco para confirmar la transacción.</p>
              </div>

              <button type="button" (click)="processPSE()" [disabled]="!pseBank || processing" class="btn-primary" style="width: 100%; padding: 16px; font-size: 1.1rem; font-weight: 700; text-transform: uppercase; letter-spacing: 1px;">
                <span *ngIf="!processing">Ir al Banco a pagar $ {{ paymentAmount | number:'1.0-0' }}</span>
                <span *ngIf="processing" style="display: flex; align-items: center; justify-content: center; gap: 10px;">
                  Conectando con el banco... <div class="luxury-spinner" style="width: 20px; height: 20px; border-width: 2px;"></div>
                </span>
              </button>
            </div>

            <!-- PAYPAL FORM -->
            <div *ngIf="selectedMethod === 'PAYPAL'">
              <div style="background: rgba(0,0,0,0.3); padding: 3rem 2rem; border-radius: 8px; border: 1px solid rgba(255,255,255,0.05); margin-bottom: 2rem; text-align: center;">
                <div style="font-size: 3rem; color: #0070ba; font-weight: 800; font-style: italic; letter-spacing: -2px; margin-bottom: 1rem;">PayPal</div>
                <p style="color: #ccc; margin-bottom: 2rem;">Paga de forma rápida y segura con tu cuenta de PayPal.</p>
                
                <button type="button" (click)="processPayPal()" [disabled]="processing" style="background: #ffc439; color: #000; border: none; border-radius: 24px; padding: 12px 30px; font-size: 1.1rem; font-weight: 600; cursor: pointer; transition: all 0.2s; min-width: 250px;">
                  <span *ngIf="!processing">Pagar $ {{ paymentAmount | number:'1.0-0' }} con PayPal</span>
                  <span *ngIf="processing">Abriendo PayPal...</span>
                </button>
              </div>
            </div>

            <p *ngIf="errorMsg" style="color: #ff4d4d; margin-top: 1.5rem; text-align: center; font-size: 0.95rem; background: rgba(255,77,77,0.1); padding: 10px; border-radius: 6px;">
              {{ errorMsg }}
            </p>
          </div>
        </div>

        <!-- Right: Order Summary -->
        <div *ngIf="booking">
           <div style="background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2rem; position: sticky; top: 100px;">
             <h3 style="font-family: var(--font-title); font-size: 1.5rem; color: var(--gold); margin-bottom: 1.5rem; border-bottom: 1px solid rgba(212,175,55,0.2); padding-bottom: 1rem;">Resumen del Pedido</h3>
             
             <div style="display: flex; flex-direction: column; gap: 1.2rem; margin-bottom: 2rem;">
               <div>
                 <span style="display: block; color: #aaa; font-size: 0.85rem; margin-bottom: 4px;">Suite</span>
                 <span style="font-weight: 600; font-size: 1.1rem;">{{ booking.suite?.name || 'Suite de Lujo' }}</span>
               </div>
               
               <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                 <div>
                   <span style="display: block; color: #aaa; font-size: 0.85rem; margin-bottom: 4px;">Check-in</span>
                   <span style="font-weight: 500;">{{ booking.checkIn | date:'longDate' }}</span>
                 </div>
                 <div>
                   <span style="display: block; color: #aaa; font-size: 0.85rem; margin-bottom: 4px;">Check-out</span>
                   <span style="font-weight: 500;">{{ booking.checkOut | date:'longDate' }}</span>
                 </div>
               </div>

               <div style="display: flex; justify-content: space-between; align-items: center; background: rgba(255,255,255,0.03); padding: 10px 15px; border-radius: 6px;">
                 <span style="color: #aaa;">Huéspedes</span>
                 <span style="font-weight: 600;">{{ booking.guests }} personas</span>
               </div>
               
               <div style="display: flex; justify-content: space-between; align-items: center; background: rgba(255,255,255,0.03); padding: 10px 15px; border-radius: 6px;">
                 <span style="color: #aaa;">Noches</span>
                 <span style="font-weight: 600;">{{ booking.nights || 1 }}</span>
               </div>

               <!-- EXTRAS IN CHECKOUT SUMMARY -->
               <div *ngIf="booking.experiences?.length || booking.airportPickup || booking.foodOrders?.length" style="margin-top: 1rem; padding-top: 1rem; border-top: 1px solid rgba(255,255,255,0.1);">
                 <h4 style="color: var(--gold); margin: 0 0 0.8rem 0; font-size: 0.95rem; text-transform: uppercase;">Extras Seleccionados</h4>
                 
                 <div *ngFor="let exp of booking.experiences" style="display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 0.9rem;">
                   <span style="color: #ccc;">{{ exp.name }}</span>
                   <span>$ {{ exp.price | number:'1.0-0' }}</span>
                 </div>
                 
                 <div *ngIf="booking.airportPickup" style="display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 0.9rem;">
                   <span style="color: #ccc;">Recogida Aeropuerto</span>
                   <span>$ 150,000</span>
                 </div>

                 <ng-container *ngFor="let food of booking.foodOrders">
                   <div style="display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 0.9rem;">
                     <span style="color: #ccc;">Comida Personalizada</span>
                     <span>$ {{ food.price | number:'1.0-0' }}</span>
                   </div>
                   <div *ngIf="food.includesBreakfastCombo" style="display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 0.85rem; padding-left: 1rem;">
                     <span style="color: #aaa;">↳ Combo Desayuno</span>
                     <span style="color: #aaa;">$ 35,000</span>
                   </div>
                 </ng-container>
               </div>
             </div>

             <div style="border-top: 1px dashed rgba(212,175,55,0.4); padding-top: 1.5rem; margin-bottom: 1.5rem;">
               <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                 <span style="font-size: 1rem; color: #ccc;">Subtotal</span>
                 <span style="font-size: 1rem; color: #fff;">$ {{ (booking.totalPrice * 0.81) | number:'1.0-0' }}</span>
               </div>
               <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                 <span style="font-size: 1rem; color: #ccc;">Impuestos (19%)</span>
                 <span style="font-size: 1rem; color: #fff;">$ {{ (booking.totalPrice * 0.19) | number:'1.0-0' }}</span>
               </div>
             </div>

             <div *ngIf="booking.paidAmount && booking.paidAmount > 0" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 0.95rem;">
               <span style="color: #2ecc71;">Abonado previamente</span>
               <span style="color: #2ecc71; font-weight: bold;">-$ {{ booking.paidAmount | number:'1.0-0' }}</span>
             </div>
             <div style="border-top: 2px solid rgba(212,175,55,0.3); padding-top: 1.5rem; display: flex; justify-content: space-between; align-items: center;">
               <span style="font-size: 1.2rem; color: #fff; font-weight: bold;">Total a Pagar Ahora</span>
               <span style="font-size: 1.8rem; font-weight: 800; color: var(--gold);">$ {{ paymentAmount | number:'1.0-0' }}</span>
             </div>
             
             <div style="margin-top: 2rem; text-align: center; color: #666; font-size: 0.8rem; display: flex; align-items: center; justify-content: center; gap: 8px;">
               🔒 Transacción segura encriptada con SSL a 256 bits.
             </div>
           </div>
        </div>

      </div>
    </div>
  `
})
export class CheckoutComponent implements OnInit, OnDestroy {
  Math = Math;
  bookingId: string | null = null;
  booking: Booking | null = null;
  loading = true;
  processing = false;
  errorMsg = '';

  selectedMethod: 'CARD' | 'PSE' | 'PAYPAL' = 'CARD';
  pseBank: string = '';

  paymentMode: 'full' | 'partial' = 'full';
  timerText: string = '15:00';
  timerSeconds: number = 900;
  timerInterval: any = null;

  paymentForm: FormGroup;

  get balanceDue(): number {
    if (!this.booking) return 0;
    if (this.booking.balanceDue !== undefined) return this.booking.balanceDue;
    return Math.max(0, this.booking.totalPrice - (this.booking.paidAmount || 0));
  }

  get paymentAmount(): number {
    if (this.paymentMode === 'partial') {
      return Math.round(this.balanceDue / 2);
    }
    return this.balanceDue;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: ApiService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {
    this.paymentForm = this.fb.group({
      cardName: ['', Validators.required],
      cardNumber: ['', [Validators.required, Validators.minLength(19)]],
      expiry: ['', [Validators.required, Validators.pattern(/^(0[1-9]|1[0-2])\/\d{2}$/)]],
      cvv: ['', [Validators.required, Validators.minLength(3)]]
    });
  }

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      this.bookingId = params.get('bookingId');
      if (this.bookingId) {
        this.loadBooking();
      } else {
        this.loading = false;
      }
    });
  }

  loadBooking() {
    this.api.get<any>(`/bookings/${this.bookingId}`).pipe(
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        const raw = res.data || res;
        this.booking = raw as Booking;
        if (this.booking && (this.booking.balanceDue === 0 || this.booking.paymentStatus === 'paid')) {
          this.booking = null; 
        } else if (this.booking) {
          if (!this.booking.nights) {
            const d1 = new Date(this.booking.checkIn);
            const d2 = new Date(this.booking.checkOut);
            this.booking.nights = Math.max(1, Math.round((d2.getTime() - d1.getTime()) / (1000 * 3600 * 24)));
          }
          this.initTimer();
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.booking = null;
        this.cdr.detectChanges();
      }
    });
  }

  initTimer() {
    if (this.timerInterval) clearInterval(this.timerInterval);
    if (this.booking?.expiresAt) {
      const exp = new Date(this.booking.expiresAt).getTime();
      const diff = Math.floor((exp - Date.now()) / 1000);
      this.timerSeconds = diff > 0 ? diff : 0;
    } else {
      this.timerSeconds = 900;
    }
    this.updateTimerText();

    this.timerInterval = setInterval(() => {
      if (this.timerSeconds > 0) {
        this.timerSeconds--;
        this.updateTimerText();
        this.cdr.detectChanges();
      } else {
        clearInterval(this.timerInterval);
        this.errorMsg = 'El tiempo de reserva de 15 minutos ha expirado. Por favor realiza una nueva reserva.';
        this.cdr.detectChanges();
      }
    }, 1000);
  }

  updateTimerText() {
    const mins = Math.floor(this.timerSeconds / 60);
    const secs = this.timerSeconds % 60;
    this.timerText = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  ngOnDestroy() {
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  formatCardNumber(event: Event) {
    const inputEl = event.target as HTMLInputElement;
    let input = (inputEl.value || '').replace(/\D/g, '').substring(0, 16);
    let formatted = input.match(/.{1,4}/g)?.join(' ') || '';
    this.paymentForm.get('cardNumber')?.setValue(formatted);
  }

  formatExpiry(event: Event) {
    const inputEl = event.target as HTMLInputElement;
    let input = (inputEl.value || '').replace(/\D/g, '').substring(0, 4);
    if (input.length >= 2) {
      input = input.substring(0, 2) + '/' + input.substring(2, 4);
    }
    this.paymentForm.get('expiry')?.setValue(input);
  }

  processPSE() {
    if (!this.pseBank) return;
    this.processing = true;
    this.errorMsg = '';
    this.cdr.detectChanges();
    
    // Simulate redirect delay to bank
    setTimeout(() => {
      this.executePaymentPayload({ method: 'PSE', bank: this.pseBank });
    }, 1500);
  }

  processPayPal() {
    this.processing = true;
    this.errorMsg = '';
    this.cdr.detectChanges();
    
    // Simulate opening paypal popup
    setTimeout(() => {
      this.executePaymentPayload({ method: 'PAYPAL' });
    }, 1500);
  }

  processPayment() {
    if (this.paymentForm.invalid || !this.booking) {
      this.paymentForm.markAllAsTouched();
      return;
    }

    this.processing = true;
    this.errorMsg = '';
    this.cdr.detectChanges();

    const rawNumber = (this.paymentForm.value.cardNumber || '').replace(/\s+/g, '');
    const payload = {
      method: 'CREDIT_CARD',
      cardNumber: rawNumber,
      cardDetails: {
         name: this.paymentForm.value.cardName,
         last4: rawNumber.slice(-4)
      }
    };
    
    // Slight delay to make the CC validation feel real
    setTimeout(() => {
      this.executePaymentPayload(payload);
    }, 1000);
  }

  private executePaymentPayload(methodPayload: any) {
    if (!this.booking) {
      this.errorMsg = 'No hay una reserva activa para procesar';
      this.processing = false;
      this.cdr.detectChanges();
      return;
    }

    const currentBooking = this.booking;
    const targetBookingId = currentBooking.id || currentBooking._id || this.bookingId;

    const payload = {
      bookingId: targetBookingId,
      amount: this.paymentAmount,
      paymentMode: this.paymentMode,
      ...methodPayload
    };

    this.api.post<any>('/payments/process', payload).pipe(
      finalize(() => {
        this.processing = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.router.navigate(['/reservas', targetBookingId, 'comprobante']);
      },
      error: (err) => {
        this.errorMsg = err?.error?.message || err?.message || 'Transacción declinada por el banco simulado. Por favor intenta de nuevo.';
        this.cdr.detectChanges();
      }
    });
  }
}

