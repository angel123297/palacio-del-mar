import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-faq-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="faq" style="padding: 4rem 1.5rem; background-color: #0d0d0d;">
      <div style="max-width: 900px; margin: 0 auto;">
        <div className="rooms-header" style="text-align: center; margin-bottom: 3rem;">
          <p className="sec-label">Preguntas frecuentes</p>
          <h2 className="sec-title">Resuelve tus dudas antes de viajar</h2>
        </div>

        <div style="display: flex; flex-direction: column; gap: 1rem;">
          <div
            *ngFor="let faq of faqs; let idx = index"
            style="background: rgba(255, 255, 255, 0.02); border: 1px solid rgba(212, 175, 55, 0.2); border-radius: 8px; overflow: hidden;"
          >
            <button
              type="button"
              (click)="toggle(idx)"
              style="width: 100%; padding: 1.25rem 1.5rem; display: flex; justify-content: space-between; align-items: center; background: none; border: none; color: #ffffff; font-size: 1.05rem; font-weight: 600; cursor: pointer; text-align: left;"
            >
              <span>{{ faq.q }}</span>
              <span style="color: var(--gold); font-size: 1.3rem; margin-left: 1rem;">
                {{ openIdx === idx ? '−' : '+' }}
              </span>
            </button>
            <div
              *ngIf="openIdx === idx"
              style="padding: 0 1.5rem 1.25rem 1.5rem; color: #cccccc; line-height: 1.6; border-top: 1px solid rgba(255, 255, 255, 0.05);"
            >
              <p style="margin: 0;">{{ faq.a }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class FaqSectionComponent {
  openIdx: number | null = 0;

  faqs = [
    {
      q: '¿Cuáles son los horarios de Check-in y Check-out?',
      a: 'El Check-in está disponible a partir de las 15:00 hrs y el Check-out se realiza hasta las 12:00 hrs del mediodía. Contamos con servicio de custodia de equipaje y late check-out bajo disponibilidad.'
    },
    {
      q: '¿Cuál es la política de cancelación de reservas?',
      a: 'Puedes cancelar tu reserva sin penalización hasta 7 días antes de la fecha de entrada con reembolso completo a tu cuenta. Para cancelaciones con menos de 7 días se aplica un cobro del 10%.'
    },
    {
      q: '¿Ofrecen servicio de transporte desde el Aeropuerto Rafael Núñez?',
      a: 'Sí, disponemos de servicio de traslado privado VIP en camioneta de lujo desde el aeropuerto hacia cualquiera de nuestras 4 sucursales.'
    },
    {
      q: '¿Qué formas de pago aceptan?',
      a: 'Aceptamos tarjetas de crédito y débito (Visa, Mastercard, American Express), transferencias bancarias, PSE y pagos con saldo a través de nuestra pasarela segura integrada.'
    }
  ];

  toggle(idx: number): void {
    this.openIdx = this.openIdx === idx ? null : idx;
  }
}
