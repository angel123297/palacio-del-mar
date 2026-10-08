import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-contact-section',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section id="contact" style="padding: 4rem 1.5rem; background-color: #121212; border-top: 1px solid rgba(212,175,55,0.15);">
      <div style="max-width: 1000px; margin: 0 auto; display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 3rem;">
        <div>
          <p className="sec-label">Atención personalizada</p>
          <h2 className="sec-title">Eventos, Bodas y Consultas VIP</h2>
          <p style="color: #cccccc; line-height: 1.6; margin-bottom: 2rem;">
            ¿Planeas una boda colonial en Cartagena, un retiro corporativo o una celebración exclusiva en nuestras sucursales? Nuestro equipo de concierge planificará cada detalle.
          </p>
          <div style="display: flex; flex-direction: column; gap: 1rem; color: #e0e0e0;">
            <div>
              <strong style="color: var(--gold); display: block;">📍 Dirección Principal</strong>
              <span>Calle del Santísimo # 8-12, Centro Histórico, Cartagena de Indias</span>
            </div>
            <div>
              <strong style="color: var(--gold); display: block;">📞 Teléfono & WhatsApp</strong>
              <span>+57 300 987 6543 / +57 (605) 650 1234</span>
            </div>
            <div>
              <strong style="color: var(--gold); display: block;">✉️ Correo Electrónico</strong>
              <span>reservas&#64;palaciomar.co · eventos&#64;palaciomar.co</span>
            </div>
          </div>
        </div>

        <form
          (ngSubmit)="handleSubmit()"
          style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(212, 175, 55, 0.2); border-radius: 12px; padding: 2rem; display: flex; flex-direction: column; gap: 1rem;"
        >
          <h3 style="color: #ffffff; margin: 0 0 0.5rem 0;">Envíanos tu solicitud</h3>
          <div>
            <label className="field-label">Nombre completo</label>
            <input
              type="text"
              required
              placeholder="Ej: Alejandro Silva"
              [(ngModel)]="formData.name"
              name="name"
              style="width: 100%;"
            />
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
            <div>
              <label className="field-label">Correo electrónico</label>
              <input
                type="email"
                required
                placeholder="ejemplo@correo.com"
                [(ngModel)]="formData.email"
                name="email"
                style="width: 100%;"
              />
            </div>
            <div>
              <label className="field-label">Teléfono</label>
              <input
                type="tel"
                placeholder="+57 300..."
                [(ngModel)]="formData.phone"
                name="phone"
                style="width: 100%;"
              />
            </div>
          </div>

          <div>
            <label className="field-label">Tipo de solicitud</label>
            <select [(ngModel)]="formData.eventType" name="eventType" style="width: 100%;">
              <option value="consulta">Consulta General</option>
              <option value="boda">Boda o Aniversario</option>
              <option value="corporativo">Evento Corporativo</option>
              <option value="yate">Alquiler Privado de Yate</option>
            </select>
          </div>

          <div>
            <label className="field-label">Mensaje o requerimientos especiales</label>
            <textarea
              rows="4"
              required
              placeholder="Cuéntanos sobre tus fechas estimadas, número de invitados o requerimientos..."
              [(ngModel)]="formData.message"
              name="message"
              style="width: 100%;"
            ></textarea>
          </div>

          <button className="btn-primary" type="submit" [disabled]="busy">
            {{ busy ? 'Enviando solicitud…' : 'Enviar Mensaje' }}
          </button>
          <p *ngIf="sentSuccess" style="color: #6fcf97; font-size: 0.9rem; margin: 0; text-align: center;">
            ¡Gracias por contactarnos! Un asesor se comunicará contigo en breve.
          </p>
        </form>
      </div>
    </section>
  `
})
export class ContactSectionComponent {
  formData = {
    name: '',
    email: '',
    phone: '',
    eventType: 'consulta',
    message: ''
  };
  busy = false;
  sentSuccess = false;

  handleSubmit(): void {
    this.busy = true;
    setTimeout(() => {
      this.busy = false;
      this.sentSuccess = true;
      this.formData = { name: '', email: '', phone: '', eventType: 'consulta', message: '' };
      setTimeout(() => this.sentSuccess = false, 5000);
    }, 800);
  }
}
