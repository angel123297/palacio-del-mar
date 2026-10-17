import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { DateRangePickerComponent } from '../../components/date-range-picker.component';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { SearchStateService } from '../../services/search-state.service';
import { Suite, Experience } from '../../models/types';
import { format } from 'date-fns';
import { AuthModalComponent } from '../../components/auth-modal/auth-modal.component';

const bogotaTodayIso = (): string => {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  } catch {
    return format(new Date(), 'yyyy-MM-dd');
  }
};

const addDaysIso = (iso: string, days: number): string => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
};

@Component({
  selector: 'app-suite-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, DateRangePickerComponent, AuthModalComponent],
  templateUrl: './suite-detail.component.html',
  styleUrls: ['./suite-detail.component.css']
})
export class SuiteDetailComponent implements OnInit {
  suite: Suite | null = null;
  loading = true;
  step = 1;
  
  // Step 1: Basic Booking Info
  today = bogotaTodayIso();
  checkIn = this.today;
  checkOut = addDaysIso(this.today, 2);
  guests = 2;

  // Step 2: Experiences
  experiences: Experience[] = [];
  selectedExperiences: { exp: Experience; qty: number }[] = [];

  // Step 3: VIP Services
  airportPickup = false;
  petFriendly = false;
  needsCrib = false;
  wantFood = false;
  foodType = '';
  customFood = '';
  breakfastCombo = false;

  // Step 4: Summary & Payment
  guestName = '';
  guestEmail = '';
  guestPhone = '';
  bookingBusy = false;
  message = '';
  isError = false;
  paymentSuccess = false;
  
  authModalOpen = false;
  authMode: 'login' | 'register' = 'login';
  
  openAuth() {
    this.authModalOpen = true;
  }
  
  onAuthClose() {
    this.authModalOpen = false;
    if (this.auth.isLoggedIn()) {
       const user = this.auth.currentUserValue;
       if (user) {
         this.guestName = user.name || '';
         this.guestEmail = user.email || '';
         this.guestPhone = user.profile?.phone || user.phone || '';
       }
    }
  }

  foodOptions = [
    { id: 't1', name: 'Cazuela de Mariscos (Típico)' },
    { id: 't2', name: 'Mojarra Frita con Arroz de Coco (Típico)' },
    { id: 't3', name: 'Posta Cartagenera (Típico)' },
    { id: 'n1', name: 'Hamburguesa Artesanal (Normal)' },
    { id: 'n2', name: 'Pasta Alfredo (Normal)' },
    { id: 'other', name: 'Otro (Especificar)' }
  ];

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    public auth: AuthService,
    private searchState: SearchStateService,
    private cdr: ChangeDetectorRef,
    private sanitizer: DomSanitizer,
    private router: Router
  ) {}

  get mapUrl(): SafeResourceUrl {
    const address = (typeof this.suite?.branch === 'object' ? this.suite.branch?.address : null) || 'Cartagena, Colombia';
    const url = `https://maps.google.com/maps?q=${encodeURIComponent(address)}&t=&z=16&ie=UTF8&iwloc=&output=embed`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  get branchIdentifier(): string {
    if (!this.suite) return '';
    const b: any = this.suite.branch;
    return b?.id || b?._id || b?.slug || this.suite.propertyId || (typeof b === 'string' ? b : '') || '';
  }

  ngOnInit(): void {
    const filters = this.searchState.filters();
    if (filters.checkIn) this.checkIn = filters.checkIn;
    if (filters.checkOut) this.checkOut = filters.checkOut;
    if (filters.adults) this.guests = filters.adults;

    if (this.auth.isLoggedIn()) {
       const user = this.auth.currentUserValue;
       if (user) {
         this.guestName = user.name || '';
         this.guestEmail = user.email || '';
         this.guestPhone = user.profile?.phone || user.phone || '';
       }
    }

    const idOrSlug = this.route.snapshot.paramMap.get('idOrSlug');
    if (idOrSlug) {
      this.api.get<any>(`/suites/${idOrSlug}`).subscribe({
        next: (res) => {
          this.suite = res.data || res;
          this.loading = false;
          this.fetchLiveQuote();
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        }
      });
      
      this.api.get<any>('/experiences').subscribe({
        next: (res) => {
           this.experiences = res.data || res;
        }
      });
    }
  }

  dynamicPricingNotes: string[] = [];
  dynamicQuoteTotal: number | null = null;

  fetchLiveQuote() {
    const sId = this.suite?.id || this.suite?._id;
    if (!sId || !this.checkIn || !this.checkOut) return;
    this.api.post<any>('/suites/calculate-price', {
      suiteId: sId,
      checkIn: this.checkIn,
      checkOut: this.checkOut
    }).subscribe({
      next: (res) => {
        const d = res?.data || res;
        this.dynamicPricingNotes = d?.dynamicPricingNotes || [];
        if (d?.total) {
          this.dynamicQuoteTotal = d.total;
        }
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  validateDates() {
    if (this.checkOut <= this.checkIn) {
      const d = new Date(this.checkIn + 'T12:00:00');
      d.setDate(d.getDate() + 1);
      this.checkOut = format(d, 'yyyy-MM-dd');
    }
    this.fetchLiveQuote();
  }

  get nights(): number {
    const diffTime = Math.abs(new Date(this.checkOut).getTime() - new Date(this.checkIn).getTime());
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24)) || 1;
  }

  get totalRoomPrice(): number {
    if (this.dynamicQuoteTotal != null) {
      return this.dynamicQuoteTotal;
    }
    const base = (this.suite?.basePrice || 0) * this.nights;
    const taxes = Math.round(base * 0.19);
    return base + taxes;
  }

  get totalExperiencesPrice(): number {
    return this.selectedExperiences.reduce((sum, item) => sum + (item.exp.price * item.qty), 0);
  }

  get totalVipPrice(): number {
    let total = 0;
    if (this.airportPickup) total += 150000;
    if (this.petFriendly) total += 80000;
    if (this.wantFood && this.foodType) {
      total += 80000;
      if (this.breakfastCombo) total += 35000;
    }
    return total;
  }

  // Mismas reglas que BookingService: $120.000 por huésped adicional (sobre 2) y noche.
  get extraGuestsFee(): number {
    const guests = Number(this.guests) || 0;
    return guests > 2 ? (guests - 2) * 120000 * this.nights : 0;
  }

  // Depósito de garantía reembolsable que el backend suma al total.
  readonly guaranteeDeposit = 100000;

  get grandTotal(): number {
    return this.totalRoomPrice + this.totalExperiencesPrice + this.totalVipPrice + this.extraGuestsFee + this.guaranteeDeposit;
  }

  toggleExperience(exp: Experience) {
    const idx = this.selectedExperiences.findIndex(e => e.exp.id === exp.id);
    if (idx > -1) {
      this.selectedExperiences.splice(idx, 1);
    } else {
      this.selectedExperiences.push({ exp, qty: 1 });
    }
  }
  
  isExperienceSelected(exp: Experience): boolean {
    return this.selectedExperiences.findIndex(e => e.exp.id === exp.id) > -1;
  }

  nextStep() {
    if (this.step < 4) this.step++;
    window.scrollTo(0,0);
  }

  prevStep() {
    if (this.step > 1) this.step--;
    window.scrollTo(0,0);
  }
  
  goToStep(s: number) {
    this.step = s;
    window.scrollTo(0,0);
  }

  createBooking(): void {
    if (!this.auth.isLoggedIn()) {
      this.message = 'Debes iniciar sesión para confirmar la reserva.';
      this.isError = true;
      return;
    }
    
    this.bookingBusy = true;
    this.message = '';

    const foodOrders = [];
    if (this.wantFood && this.foodType) {
       const selectedDish = this.foodType === 'other' ? this.customFood : this.foodOptions.find(f => f.id === this.foodType)?.name;
       foodOrders.push({
          dishName: selectedDish,
          foodName: selectedDish,
          price: 80000,
          includesBreakfastCombo: this.breakfastCombo,
          specialInstructions: this.foodType === 'other' ? (this.customFood || 'Pedido personalizado') : ''
       });
    }

    this.api.post<any>('/bookings', {
      suiteId: this.suite?.id || this.suite?._id,
      checkIn: this.checkIn,
      checkOut: this.checkOut,
      guests: Number(this.guests),
      airportPickup: this.airportPickup,
      petFriendly: this.petFriendly,
      needsCrib: this.needsCrib,
      foodOrders: foodOrders,
      experiences: this.selectedExperiences.map(e => e.exp.id),
      guestName: this.guestName,
      guestEmail: this.guestEmail,
      guestPhone: this.guestPhone
    }).subscribe({
      next: (res) => {
        this.bookingBusy = false;
        this.cdr.detectChanges();
        if (res.data && (res.data.id || res.data._id)) {
           this.router.navigate(['/checkout', res.data.id || res.data._id]);
        } else {
           this.router.navigate(['/mis-reservas']);
        }
      },
      error: (err) => {
        this.bookingBusy = false;
        this.message = err.error?.message || 'Error al procesar la reserva.';
        this.isError = true;
        this.cdr.detectChanges();
      }
    });
  }
}
