import { Component, OnInit, ChangeDetectorRef, Optional } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
// @ts-ignore
import { renderSVG } from '../../utils/qr';

@Component({
  selector: 'app-receipt',
  standalone: true,
  imports: [CommonModule, RouterModule, DatePipe, FormsModule],
  template: `
    <div style="min-height: 100vh; background: #fff; color: #333; padding: 2rem; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;">
      
      <!-- Controles no imprimibles -->
      <div class="no-print" style="max-width: 800px; margin: 0 auto 2rem auto; display: flex; justify-content: space-between; align-items: center;">
        <a [routerLink]="['/reservas', bookingId]" style="color: #666; text-decoration: none; font-weight: bold;">← Volver al detalle</a>
        <button (click)="print()" style="background: #d4af37; color: #fff; border: none; padding: 10px 20px; font-size: 1rem; border-radius: 4px; cursor: pointer; font-weight: bold;">Imprimir / Guardar PDF</button>
      </div>

      <div *ngIf="loading" style="text-align: center; margin-top: 4rem;">
        <p>Cargando factura...</p>
      </div>

      <div *ngIf="!loading && !booking" style="text-align: center; margin-top: 4rem; color: red;">
        <p>No se pudo cargar el recibo.</p>
      </div>

      <!-- Factura / Recibo imprimible -->
      <div *ngIf="!loading && booking" class="receipt-container">
        
        <div class="receipt-header">
          <div>
            <h1 style="margin: 0; font-size: 2rem; font-family: 'Cormorant Garamond', serif; color: #000;">Palacio del Mar</h1>
            <p style="margin: 5px 0; color: #666; font-size: 0.9rem;">Cartagena de Indias, Colombia</p>
            <p style="margin: 0; color: #666; font-size: 0.9rem;">NIT: 900.123.456-7</p>
            <p style="margin: 0; color: #666; font-size: 0.9rem;">contacto@palaciodelmar.com</p>
          </div>
          <div style="text-align: right;">
            <h2 style="margin: 0; font-size: 2.5rem; color: #d4af37; text-transform: uppercase;">Factura & Voucher</h2>
            <p style="margin: 5px 0; font-weight: bold;">N° {{ bookingCodeDisplay | uppercase }}</p>
            <p style="margin: 0; color: #555;">Fecha de emisión: {{ today | date:'mediumDate' }}</p>
          </div>
        </div>

        <!-- Tarjeta de Acceso Inteligente / Pase de Cerradura Digital -->
        <div class="access-pass" style="margin-top: 2rem; background: #faf9f6; border: 2px solid #d4af37; border-radius: 8px; padding: 1.5rem; display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 1.5rem;">
          <div style="flex: 1; min-width: 260px;">
            <span style="display: inline-block; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; color: #8c7329; font-weight: 700;">Pase de Acceso Inteligente</span>
            <h3 style="margin: 4px 0 10px 0; font-size: 1.25rem; color: #111;">{{ suiteName }}</h3>
            
            <div style="margin-bottom: 12px;">
              <span style="font-size: 0.85rem; color: #666; display: block;">PIN de Entrada (Cerradura Inteligente):</span>
              <div style="display: inline-block; font-family: monospace; font-size: 1.6rem; font-weight: bold; letter-spacing: 4px; color: #0d0d0d; background: #fff; border: 1px dashed #d4af37; padding: 4px 14px; border-radius: 4px; margin-top: 4px;">
                {{ smartLockPinDisplay }}
              </div>
            </div>

            <div style="display: flex; gap: 1.5rem; font-size: 0.85rem; color: #444;">
              <div><strong>Llegada:</strong> {{ booking.checkIn | date:'mediumDate' }} <span style="color:#d4af37; font-weight:bold;">(15:00)</span></div>
              <div><strong>Salida:</strong> {{ booking.checkOut | date:'mediumDate' }} <span style="color:#d4af37; font-weight:bold;">(13:00)</span></div>
            </div>
            <p style="margin: 8px 0 0 0; font-size: 0.75rem; color: #777;">Presenta este comprobante en recepción o digita el PIN directamente en la cerradura electrónica.</p>
          </div>

          <div style="text-align: center; background: #fff; padding: 12px; border-radius: 6px; border: 1px solid #e5e5e5; box-shadow: 0 2px 8px rgba(0,0,0,0.04);">
            <div [innerHTML]="qrSvgSafe" style="width: 140px; height: 140px; margin: 0 auto; display: flex; align-items: center; justify-content: center;"></div>
            <span style="display: block; font-size: 0.7rem; color: #666; margin-top: 6px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">Escanear para Check-in</span>
          </div>
        </div>

        <div style="display: flex; justify-content: space-between; margin-top: 2rem; border-top: 2px solid #eee; padding-top: 1.5rem;">
          <div>
            <h3 style="margin-top: 0; color: #444; font-size: 1.1rem; text-transform: uppercase;">Facturado a:</h3>
            <p style="margin: 5px 0; font-weight: bold; font-size: 1.1rem;">{{ guestName }}</p>
            <p style="margin: 5px 0; color: #555;">{{ guestEmail }}</p>
          </div>
          <div style="text-align: right;">
            <h3 style="margin-top: 0; color: #444; font-size: 1.1rem; text-transform: uppercase;">Estado de Pago:</h3>
            <div style="display: inline-block; padding: 5px 15px; border-radius: 4px; font-weight: bold; border: 2px solid; margin-top: 5px;" 
                 [ngStyle]="{'color': isPaid ? '#2ecc71' : (booking.status === 'pending' ? '#e67e22' : '#e74c3c'), 'border-color': isPaid ? '#2ecc71' : (booking.status === 'pending' ? '#e67e22' : '#e74c3c')}">
              {{ isPaid ? 'PAGADO' : (booking.status === 'pending' ? 'PENDIENTE' : 'CANCELADO') }}
            </div>
          </div>
        </div>

        <table style="width: 100%; margin-top: 2rem; border-collapse: collapse;">
          <thead>
            <tr style="background: #f8f9fa; border-bottom: 2px solid #ddd;">
              <th style="padding: 12px; text-align: left; color: #333;">Descripción del Servicio</th>
              <th style="padding: 12px; text-align: center; color: #333;">Llegada / Salida</th>
              <th style="padding: 12px; text-align: right; color: #333;">Huéspedes</th>
              <th style="padding: 12px; text-align: right; color: #333;">Total</th>
            </tr>
          </thead>
          <tbody>
            <tr style="border-bottom: 1px solid #eee;">
              <td style="padding: 15px 12px;">
                <strong style="display: block; font-size: 1.1rem;">Alojamiento - {{ suiteName }}</strong>
                <span style="color: #666; font-size: 0.9rem;">Servicio de hotelería</span>
              </td>
              <td style="padding: 15px 12px; text-align: center; color: #555;">
                {{ booking.checkIn | date:'shortDate' }} a {{ booking.checkOut | date:'shortDate' }}
              </td>
              <td style="padding: 15px 12px; text-align: right; color: #555;">
                {{ booking.guests }}
              </td>
              <td style="padding: 15px 12px; text-align: right; font-weight: bold; font-size: 1.1rem;">
                $ {{ booking.totalPrice | number:'1.0-0' }}
              </td>
            </tr>
          </tbody>
        </table>

        <div style="display: flex; justify-content: flex-end; margin-top: 2rem;">
          <div style="width: 300px;">
            <div style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee;">
              <span style="color: #666;">Subtotal:</span>
              <span>$ {{ (booking.totalPrice / 1.19) | number:'1.0-0' }}</span>
            </div>
            <div style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 2px solid #333;">
              <span style="color: #666;">IVA (19%):</span>
              <span>$ {{ (booking.totalPrice - (booking.totalPrice / 1.19)) | number:'1.0-0' }}</span>
            </div>
            <div style="display: flex; justify-content: space-between; padding: 15px 0; font-size: 1.5rem; font-weight: bold; color: #000;">
              <span>Total pagado:</span>
              <span style="color: #d4af37;">$ {{ booking.totalPrice | number:'1.0-0' }}</span>
            </div>
          </div>
        </div>

        <!-- Módulo de Reseña Verificada (post-check-out, no imprimible) -->
        <div *ngIf="canLeaveReview" class="no-print" style="margin-top: 2.5rem; background: #fdfbf7; border: 1px solid #e8dfc8; border-radius: 8px; padding: 1.5rem;">
          <div *ngIf="!reviewSubmitted">
            <h4 style="margin: 0 0 6px 0; color: #8c7329; font-size: 1.1rem;">Califica tu experiencia de estadía</h4>
            <p style="margin: 0 0 12px 0; font-size: 0.85rem; color: #666;">Tu calificación como huésped verificado ayuda a mantener la calidad del marketplace.</p>
            
            <div style="display: flex; gap: 8px; margin-bottom: 12px; align-items: center;">
              <span style="font-size: 0.9rem; font-weight: bold; margin-right: 6px;">Puntuación:</span>
              <button *ngFor="let star of [1,2,3,4,5]" 
                      type="button"
                      (click)="reviewRating = star"
                      [style.color]="star <= reviewRating ? '#d4af37' : '#ccc'"
                      style="background: none; border: none; font-size: 1.5rem; cursor: pointer; padding: 0 2px;">
                ★
              </button>
            </div>

            <textarea [(ngModel)]="reviewComment" 
                      placeholder="Escribe tu opinión sobre el alojamiento, limpieza y atención..."
                      rows="3"
                      style="width: 100%; border: 1px solid #ddd; border-radius: 4px; padding: 8px; font-family: inherit; font-size: 0.9rem; box-sizing: border-box;"></textarea>

            <div *ngIf="reviewError" style="color: #e74c3c; font-size: 0.85rem; margin-top: 8px;">
              {{ reviewError }}
            </div>

            <div style="margin-top: 10px; display: flex; justify-content: flex-end;">
              <button (click)="submitReview()" 
                      [disabled]="reviewSubmitting || !reviewComment.trim()"
                      style="background: #0d0d0d; color: #d4af37; border: 1px solid #d4af37; padding: 8px 18px; border-radius: 4px; font-weight: bold; cursor: pointer;">
                {{ reviewSubmitting ? 'Enviando...' : 'Publicar Reseña Verificada' }}
              </button>
            </div>
          </div>

          <div *ngIf="reviewSubmitted" style="text-align: center; color: #2ecc71; padding: 10px;">
            <strong>✓ ¡Gracias por tu reseña verificada! Ha sido registrada exitosamente.</strong>
          </div>
        </div>

        <div style="margin-top: 4rem; text-align: center; color: #888; font-size: 0.85rem; border-top: 1px solid #eee; padding-top: 2rem;">
          <p>Gracias por elegir Palacio del Mar. Esperamos que disfrute su estancia con nosotros.</p>
          <p>Este documento es un comprobante de reserva y pago electrónico con validez digital. Soporte: reservas@palaciodelmar.com</p>
        </div>

      </div>
    </div>
  `,
  styles: [`
    .receipt-container {
      max-width: 800px;
      margin: 0 auto;
      background: #fff;
      padding: 3rem;
      border: 1px solid #ddd;
      box-shadow: 0 5px 20px rgba(0,0,0,0.05);
    }
    .receipt-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }
    @media print {
      body * { visibility: hidden; }
      .no-print { display: none !important; }
      .receipt-container, .receipt-container * { visibility: visible; }
      .receipt-container { position: absolute; left: 0; top: 0; width: 100%; border: none; box-shadow: none; padding: 0; margin: 0; }
    }
  `]
})
export class ReceiptComponent implements OnInit {
  bookingId: string = '';
  booking: any = null;
  loading = true;
  today = new Date();
  qrSvgSafe: SafeHtml = '';

  // Reseña post-estadía
  reviewRating = 5;
  reviewComment = '';
  reviewSubmitting = false;
  reviewSubmitted = false;
  reviewError = '';

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private cdr: ChangeDetectorRef,
    private sanitizer: DomSanitizer,
    @Optional() private auth?: AuthService
  ) {}

  ngOnInit(): void {
    this.bookingId = this.route.snapshot.paramMap.get('id') || '';
    if (this.bookingId) {
      this.api.get<any>(`/bookings/${this.bookingId}`).subscribe({
        next: (res) => {
          this.booking = res.data || res;
          this.generateQr();
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        }
      });
    } else {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  generateQr(): void {
    if (!this.booking) return;
    try {
      const rawCode = String(this.booking.bookingCode || this.booking.id || this.booking._id || 'RESERVA');
      const rawPin = String(this.booking.smartLockPin || 'PENDIENTE');
      const code = rawCode.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 32) || 'RESERVA';
      const pin = rawPin.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 16) || 'PENDIENTE';
      const payload = `PDM:BK:${code}:PIN:${pin}`;
      const svg = renderSVG(payload, { border: 1 });
      this.qrSvgSafe = this.sanitizer.bypassSecurityTrustHtml(svg);
    } catch (e) {
      this.qrSvgSafe = '';
    }
  }

  print(): void {
    window.print();
  }

  get isPaid(): boolean {
    const s = this.booking?.status?.toLowerCase();
    return s === 'confirmed' || s === 'checked_in' || s === 'checked_out' || s === 'completed';
  }

  get bookingCodeDisplay(): string {
    return this.booking?.bookingCode || this.booking?._id?.substring(0, 8) || this.booking?.id?.substring(0, 8) || '';
  }

  get smartLockPinDisplay(): string {
    return this.booking?.smartLockPin || 'Pendiente de asignación';
  }

  get suiteName(): string {
    if (!this.booking?.suite) return 'Suite';
    return typeof this.booking.suite === 'string' ? this.booking.suite : (this.booking.suite.name || 'Suite');
  }

  get guestName(): string {
    return this.booking?.guestDetails?.fullName || this.booking?.guestName || this.booking?.user?.name || 'Huésped';
  }

  get guestEmail(): string {
    return this.booking?.guestDetails?.email || this.booking?.guestEmail || this.booking?.user?.email || '';
  }

  get canLeaveReview(): boolean {
    const s = this.booking?.status?.toLowerCase();
    const isCompleted = s === 'checked_out' || s === 'completed';
    if (!isCompleted) return false;
    const user = this.auth?.currentUserValue;
    if (user) {
      const role = (user.role || '').toLowerCase();
      if (role === 'host' || role === 'anfitrion' || role === 'admin') {
        const uId = user.id || user._id;
        const bUser = typeof this.booking?.user === 'object' ? (this.booking.user?.id || this.booking.user?._id) : (this.booking?.user || this.booking?.userId);
        if (uId && bUser && uId !== bUser) {
          return false;
        }
      }
    }
    return true;
  }

  submitReview(): void {
    if (this.reviewSubmitting || !this.bookingId || !this.reviewComment.trim()) return;
    this.reviewSubmitting = true;
    this.reviewError = '';

    this.api.post('/reviews/verified', {
      bookingId: this.booking._id || this.booking.id || this.bookingId,
      rating: this.reviewRating,
      comment: this.reviewComment.trim()
    }).subscribe({
      next: () => {
        this.reviewSubmitting = false;
        this.reviewSubmitted = true;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.reviewSubmitting = false;
        this.reviewError = err?.error?.message || 'No se pudo enviar la reseña. Inténtalo de nuevo.';
        this.cdr.detectChanges();
      }
    });
  }
}

