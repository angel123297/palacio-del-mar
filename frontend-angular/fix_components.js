const fs = require('fs');

// 1. Fix SuiteDetailComponent
let suiteDetail = fs.readFileSync('src/app/pages/suite-detail/suite-detail.component.ts', 'utf8');

// Add empty state
suiteDetail = suiteDetail.replace(
  `      <div *ngIf="!loading && suite">`,
  `      <div *ngIf="!loading && !suite" style="text-align: center; color: #ff4d4d; padding: 4rem;">
        <h3>La suite que buscas no existe o no está disponible.</h3>
        <a routerLink="/" style="color: var(--gold); text-decoration: none; margin-top: 1rem; display: inline-block;">← Volver al inicio</a>
      </div>

      <div *ngIf="!loading && suite">`
);

// Fix guests select
suiteDetail = suiteDetail.replace(
  /<select \[\(ngModel\)\]="guests"(.*?)>[\s\S]*?<\/select>/,
  `<select [(ngModel)]="guests" $1>
                  <option *ngFor="let opt of guestOptions" [value]="opt" style="background-color: #141414; color: #f5e6c8;">
                    {{ opt }} {{ opt === 1 ? 'Huésped' : 'Huéspedes' }}
                  </option>
                </select>`
);

// Add guestOptions getter
suiteDetail = suiteDetail.replace(
  `  guests = 2;`,
  `  guests = 2;

  get guestOptions(): number[] {
    const max = Math.max(1, this.suite?.maxGuests || (this.suite as any)?.capacity || 2);
    const opts = [];
    for (let i = 1; i <= max; i++) opts.push(i);
    return opts;
  }`
);

// Add validation to createBooking
suiteDetail = suiteDetail.replace(
  `    if (!this.suite) return;`,
  `    if (!this.suite) return;

    if (!this.checkIn || !this.checkOut) {
      this.message = 'Por favor selecciona las fechas de llegada y salida.';
      this.isError = true;
      return;
    }
    const today = new Date().toISOString().split('T')[0];
    if (this.checkIn < today) {
      this.message = 'La fecha de llegada no puede ser en el pasado.';
      this.isError = true;
      return;
    }
    if (this.checkOut <= this.checkIn) {
      this.message = 'La fecha de salida debe ser posterior a la de llegada.';
      this.isError = true;
      return;
    }
`
);

fs.writeFileSync('src/app/pages/suite-detail/suite-detail.component.ts', suiteDetail);

// 2. Fix BookingModalComponent
let bookingModal = fs.readFileSync('src/app/components/booking-modal/booking-modal.component.ts', 'utf8');

// Hide email and phone if logged in (or prepopulated). Wait, if we use *ngIf="!isLoggedIn", we need an isLoggedIn property.
// Instead, if the fields are pre-filled we can just hide the container.
// Let's add a isLoggedIn getter.
bookingModal = bookingModal.replace(
  `  get maxGuestsCount(): number {`,
  `  get isLoggedIn(): boolean {
    return !!this.auth.getToken();
  }

  get maxGuestsCount(): number {`
);

// Change the email and phone fields in the modal to only show if !isLoggedIn
bookingModal = bookingModal.replace(
  `<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
              <div>
                <label class="field-label">Correo Electrónico</label>
                <input type="email" [(ngModel)]="guestEmail" name="guestEmail" required placeholder="ejemplo@correo.com" style="width: 100%;" />
              </div>
              <div>
                <label class="field-label">Teléfono</label>
                <input type="tel" [(ngModel)]="guestPhone" name="guestPhone" placeholder="+57 300..." style="width: 100%;" />
              </div>
            </div>`,
  `<div *ngIf="!isLoggedIn" style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
              <div>
                <label class="field-label">Correo Electrónico</label>
                <input type="email" [(ngModel)]="guestEmail" name="guestEmail" required placeholder="ejemplo@correo.com" style="width: 100%;" />
              </div>
              <div>
                <label class="field-label">Teléfono</label>
                <input type="tel" [(ngModel)]="guestPhone" name="guestPhone" placeholder="+57 300..." style="width: 100%;" />
              </div>
            </div>`
);

// We need to bypass email validation if logged in
bookingModal = bookingModal.replace(
  `    if (!email) missing.push('correo electrónico');`,
  `    if (!this.isLoggedIn && !email) missing.push('correo electrónico');`
);

bookingModal = bookingModal.replace(
  `    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Ingresa un correo electrónico válido.';`,
  `    if (!this.isLoggedIn && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Ingresa un correo electrónico válido.';`
);

fs.writeFileSync('src/app/components/booking-modal/booking-modal.component.ts', bookingModal);

console.log("Fixes applied.");
