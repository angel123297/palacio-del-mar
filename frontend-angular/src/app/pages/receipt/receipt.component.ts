import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-receipt',
  standalone: true,
  imports: [CommonModule, RouterModule, DatePipe],
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
            <h2 style="margin: 0; font-size: 2.5rem; color: #d4af37; text-transform: uppercase;">Factura</h2>
            <p style="margin: 5px 0; font-weight: bold;">N° {{ booking.bookingCode || booking._id?.substring(0,8) | uppercase }}</p>
            <p style="margin: 0; color: #555;">Fecha de emisión: {{ today | date:'mediumDate' }}</p>
          </div>
        </div>

        <div style="display: flex; justify-content: space-between; margin-top: 3rem; border-top: 2px solid #eee; padding-top: 2rem;">
          <div>
            <h3 style="margin-top: 0; color: #444; font-size: 1.1rem; text-transform: uppercase;">Facturado a:</h3>
            <p style="margin: 5px 0; font-weight: bold; font-size: 1.1rem;">{{ guestName }}</p>
            <p style="margin: 5px 0; color: #555;">{{ guestEmail }}</p>
          </div>
          <div style="text-align: right;">
            <h3 style="margin-top: 0; color: #444; font-size: 1.1rem; text-transform: uppercase;">Estado de Pago:</h3>
            <div style="display: inline-block; padding: 5px 15px; border-radius: 4px; font-weight: bold; border: 2px solid; margin-top: 5px;" 
                 [ngStyle]="{'color': booking.status === 'confirmed' ? '#2ecc71' : (booking.status === 'pending' ? '#e67e22' : '#e74c3c'), 'border-color': booking.status === 'confirmed' ? '#2ecc71' : (booking.status === 'pending' ? '#e67e22' : '#e74c3c')}">
              {{ booking.status === 'confirmed' ? 'PAGADO' : (booking.status === 'pending' ? 'PENDIENTE' : 'CANCELADO') }}
            </div>
          </div>
        </div>

        <table style="width: 100%; margin-top: 3rem; border-collapse: collapse;">
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

        <div style="margin-top: 5rem; text-align: center; color: #888; font-size: 0.85rem; border-top: 1px solid #eee; padding-top: 2rem;">
          <p>Gracias por elegir Palacio del Mar. Esperamos que disfrute su estancia con nosotros.</p>
          <p>Este documento es un comprobante de pago electrónico. Para soporte contáctenos a reservas@palaciodelmar.com</p>
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

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.bookingId = this.route.snapshot.paramMap.get('id') || '';
    if (this.bookingId) {
      this.api.get<any>(`/bookings/${this.bookingId}`).subscribe({
        next: (res) => {
          this.booking = res.data || res;
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

  print(): void {
    window.print();
  }

  get suiteName(): string {
    if (!this.booking?.suite) return 'Suite';
    return typeof this.booking.suite === 'string' ? this.booking.suite : this.booking.suite.name;
  }

  get guestName(): string {
    return this.booking?.guestName || this.booking?.user?.name || 'Huésped';
  }

  get guestEmail(): string {
    return this.booking?.guestEmail || this.booking?.user?.email || '';
  }
}
