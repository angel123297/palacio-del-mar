import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-reserva',
  template: '<div><p>Reserva component</p></div>',
  styleUrls: ['./reserva.component.css']
})
export class ReservaComponent {
  reservaForm: FormGroup;

  constructor(private fb: FormBuilder) {
    this.reservaForm = this.fb.group({
      email: ['', [Validators.required, Validators.pattern('^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$')]],
      guestName: ['', Validators.required],
      hotelDetails: ['', Validators.required],
      amount: ['', Validators.required]
    });
  }

  onSubmit() {
    if (this.reservaForm.valid) {
      const rawEmail = this.reservaForm.get('email')?.value || '';
      const cleanEmail = rawEmail.trim().toLowerCase();
      
      const payload = {
        ...this.reservaForm.value,
        email: cleanEmail
      };

      console.log('Enviando datos limpios al backend...', payload);
    }
  }
}
