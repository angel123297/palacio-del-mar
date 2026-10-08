const fs = require('fs');

const detailFile = 'frontend-angular/src/app/pages/suite-detail/suite-detail.component.ts';
let detailCode = fs.readFileSync(detailFile, 'utf8');

detailCode = detailCode.replace(
  `import { FormsModule } from '@angular/forms';`,
  `import { FormsModule } from '@angular/forms';\nimport { CustomDatepickerComponent } from '../../components/custom-datepicker/custom-datepicker.component';\nimport { CustomGuestpickerComponent } from '../../components/custom-guestpicker/custom-guestpicker.component';`
);

detailCode = detailCode.replace(
  `imports: [CommonModule, RouterModule, FormsModule],`,
  `imports: [CommonModule, RouterModule, FormsModule, CustomDatepickerComponent, CustomGuestpickerComponent],`
);

// Replace inputs with pickers in SuiteDetail
detailCode = detailCode.replace(
  /<div style="display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem;">[\s\S]*?<\/select>\s*<\/div>\s*<\/div>/,
  `<div style="display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem;">
              <app-custom-datepicker label="Check-in" [date]="checkIn" (dateChange)="checkIn = $event"></app-custom-datepicker>
              <app-custom-datepicker label="Check-out" [date]="checkOut" (dateChange)="checkOut = $event"></app-custom-datepicker>
              <app-custom-guestpicker [adults]="guests" [maxGuests]="suite.maxGuests || (suite as any)?.capacity || 2" (adultsChange)="guests = $event"></app-custom-guestpicker>
            </div>`
);
fs.writeFileSync(detailFile, detailCode);

const modalFile = 'frontend-angular/src/app/components/booking-modal/booking-modal.component.ts';
let modalCode = fs.readFileSync(modalFile, 'utf8');

modalCode = modalCode.replace(
  `import { FormsModule } from '@angular/forms';`,
  `import { FormsModule } from '@angular/forms';\nimport { CustomDatepickerComponent } from '../custom-datepicker/custom-datepicker.component';\nimport { CustomGuestpickerComponent } from '../custom-guestpicker/custom-guestpicker.component';`
);

modalCode = modalCode.replace(
  `imports: [CommonModule, FormsModule],`,
  `imports: [CommonModule, FormsModule, CustomDatepickerComponent, CustomGuestpickerComponent],`
);

modalCode = modalCode.replace(
  /<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">[\s\S]*?<\/div>\s*<\/div>\s*<div>\s*<label class="field-label">Huéspedes<\/label>\s*<select \[\(ngModel\)\]="guests"[\s\S]*?<\/select>\s*<\/div>/,
  `<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                <app-custom-datepicker label="Llegada" [date]="checkIn" (dateChange)="checkIn = $event; recalculateNights()"></app-custom-datepicker>
                <app-custom-datepicker label="Salida" [date]="checkOut" (dateChange)="checkOut = $event; recalculateNights()"></app-custom-datepicker>
            </div>
            <div style="margin-top: 1rem;">
                <app-custom-guestpicker [adults]="guests" [maxGuests]="maxGuestsCount" (adultsChange)="guests = $event"></app-custom-guestpicker>
            </div>`
);
fs.writeFileSync(modalFile, modalCode);
console.log("Patched pickers");
