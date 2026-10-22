import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { DateRangePickerComponent } from '../../components/date-range-picker/date-range-picker.component';
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

const FALLBACK_SUITES_CATALOG: Suite[] = [
  {
    id: 'penthouse-bocagrande',
    name: 'Penthouse Ocean Front',
    slug: 'penthouse-bocagrande',
    type: 'Suite Presidencial',
    description: 'Penthouse exclusivo de dos niveles con piscina privada infiniti al Caribe, ventanales panorámicos y mayordomo personal 24/7.',
    propertyId: 'bocagrande',
    branch: { name: 'Palacio del Mar · Bocagrande', slug: 'bocagrande', address: 'Avenida San Martín, Bocagrande, Cartagena' } as any,
    basePrice: 2450000,
    pricePerNight: 2450000,
    size: 160,
    maxGuests: 4,
    amenities: ['Piscina privada', 'Vista al mar', 'Mayordomo 24/7', 'Terraza', 'Wi-Fi 500Mbps', 'Cava de vinos'],
    mainImage: 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800'
  } as Suite,
  {
    id: 'suite-presidencial-mar',
    name: 'Suite Presidencial Mar Caribe',
    slug: 'suite-presidencial-mar',
    type: 'Presidencial',
    description: 'Vistas panorámicas a la bahía de Bocagrande, jacuzzi privado sobre terraza colonial y mayordomo personal 24/7.',
    propertyId: 'bocagrande',
    branch: { name: 'Palacio Bocagrande', slug: 'bocagrande', address: 'Cra. 1 # 5-40, Bocagrande, Cartagena' } as any,
    basePrice: 850000,
    pricePerNight: 850000,
    size: 78,
    maxGuests: 4,
    amenities: ['Jacuzzi privado', 'Vista al mar', 'Mayordomo 24/7', 'Terraza', 'Wi-Fi 500Mbps', 'Cava de vinos'],
    mainImage: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800'
  } as Suite,
  {
    id: 'suite-arte-patio-getsemani',
    name: 'Suite Arte & Patio',
    slug: 'suite-arte-patio-getsemani',
    type: 'Suite Deluxe',
    description: 'Suite con galería de arte contemporáneo independiente y zona de descanso en patio interior privado.',
    propertyId: 'getsemani',
    branch: { name: 'Palacio del Mar · Getsemaní', slug: 'getsemani', address: 'Calle del Arsenal, Getsemaní, Cartagena' } as any,
    basePrice: 1150000,
    pricePerNight: 1150000,
    size: 58,
    maxGuests: 2,
    amenities: ['Patio privado', 'Colección de arte', 'Tina hidromasaje', 'Minibar gourmet'],
    mainImage: 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800'
  } as Suite,
  {
    id: 'casona-colonial-getsemani',
    name: 'Casona Colonial Getsemaní',
    slug: 'casona-colonial-getsemani',
    type: 'Casona Colonial',
    description: 'Patio andaluz del siglo XVIII, techos de doble altura con maderas nobles y piscina interior privada.',
    propertyId: 'getsemani',
    branch: { name: 'Casona Colonial Getsemaní', slug: 'getsemani', address: 'Calle de la Sierpe # 29-11, Getsemaní, Cartagena' } as any,
    basePrice: 620000,
    pricePerNight: 620000,
    size: 65,
    maxGuests: 4,
    amenities: ['Piscina interior', 'Patio colonial', 'Desayuno de autor', 'Camas King artesanales'],
    mainImage: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800'
  } as Suite,
  {
    id: 'suite-colonial-centro',
    name: 'Suite Colonial Imperial',
    slug: 'suite-colonial-centro',
    type: 'Suite Deluxe',
    description: 'Suite histórica con patio colonial, arcos originales del siglo XVIII, tina hidromasaje y balcón.',
    propertyId: 'centro-historico',
    branch: { name: 'Palacio del Mar · Centro Histórico', slug: 'centro-historico', address: 'Calle de la Factoría, Centro Histórico, Cartagena' } as any,
    basePrice: 1310000,
    pricePerNight: 1310000,
    size: 65,
    maxGuests: 2,
    amenities: ['Patio colonial', 'Tina hidromasaje', 'Balcón', 'Desayuno de autor'],
    mainImage: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800'
  } as Suite,
  {
    id: 'suite-colonial-patio',
    name: 'Gran Suite Colonial con Balcón',
    slug: 'suite-colonial-patio',
    type: 'Imperial',
    description: 'Balcón colonial sobre las murallas, cama King artesanal y desayuno de autor.',
    propertyId: 'centro-historico',
    branch: { name: 'Palacio Imperial San Diego', slug: 'centro-historico', address: 'Calle del Curato # 38-99, Cartagena' } as any,
    basePrice: 620000,
    pricePerNight: 620000,
    size: 55,
    maxGuests: 2,
    amenities: ['Balcón colonial', 'Cama King artesanal', 'Desayuno gourmet'],
    mainImage: 'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800'
  } as Suite
];

@Component({
  selector: 'app-suite-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, DateRangePickerComponent, AuthModalComponent],
  templateUrl: './suite-detail.component.html',
  styleUrls: ['./suite-detail.component.scss']
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

  // L1: evita que un HOST reserve su propio alojamiento
  isOwnSuite = false;
  checkingOwnership = false;
  availabilityConflict = '';
  
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

  getSuiteImage(suite: Suite | null): string {
    const brokenIds = ['photo-1591088398332', 'photo-1595526114035', 'photo-1522771739844', 'photo-1566665797739'];
    const raw = suite?.mainImage || '';
    if (!raw || brokenIds.some(id => raw.includes(id))) {
      return 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800';
    }
    return raw;
  }

  getExperienceImage(exp: Experience): string {
    if (exp?.mainImage || exp?.image) return (exp.mainImage || exp.image)!;
    const name = (exp?.name || '').toLowerCase();
    if (name.includes('cena') || name.includes('romántic') || name.includes('gastronom')) {
      return 'https://images.unsplash.com/photo-1544148103-0773bf10d330?w=800';
    }
    if (name.includes('spa') || name.includes('masaje') || name.includes('relax')) {
      return 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800';
    }
    if (name.includes('yate') || name.includes('isla') || name.includes('rosario') || name.includes('barco')) {
      return 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800';
    }
    return 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800';
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.dataset['fallbackApplied']) {
      img.dataset['fallbackApplied'] = 'true';
      img.src = 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800';
    }
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
          this.checkOwnSuite();
          this.fetchLiveQuote();
          this.cdr.detectChanges();
        },
        error: () => {
          const match = FALLBACK_SUITES_CATALOG.find(s => s.slug === idOrSlug || s.id === idOrSlug) || FALLBACK_SUITES_CATALOG[0];
          this.suite = match;
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
    const diffTime = new Date(this.checkOut).getTime() - new Date(this.checkIn).getTime();
    return Math.max(1, Math.round(diffTime / (1000 * 60 * 60 * 24)));
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

  // L1: si el usuario autenticado es HOST dueño de esta propiedad, no puede reservarla aquí.
  private checkOwnSuite(): void {
    const user = this.auth.currentUserValue;
    const role = (user?.role || '').toLowerCase();
    const propertyId = this.suite?.propertyId || this.suite?.branchId;
    if (!user || role !== 'host' || !propertyId) return;

    this.checkingOwnership = true;
    this.api.get<any>('/host/properties').subscribe({
      next: (res) => {
        const owned: any[] = res?.data || res || [];
        const ownedIds = new Set(owned.map((p: any) => p?.id || p?._id).filter(Boolean));
        this.isOwnSuite = ownedIds.has(propertyId);
        this.checkingOwnership = false;
        this.cdr.detectChanges();
      },
      error: () => {
        // Ante la duda no bloqueamos: el backend tiene la última palabra.
        this.checkingOwnership = false;
        this.cdr.detectChanges();
      }
    });
  }

  private expId(exp: Experience): string {
    return (exp.id || exp._id || '') as string;
  }

  toggleExperience(exp: Experience) {
    const expKey = this.expId(exp) || exp.name;
    const idx = this.selectedExperiences.findIndex(e => (this.expId(e.exp) || e.exp.name) === expKey);
    if (idx > -1) {
      this.selectedExperiences.splice(idx, 1);
    } else {
      this.selectedExperiences.push({ exp, qty: 1 });
    }
  }

  isExperienceSelected(exp: Experience): boolean {
    const expKey = this.expId(exp) || exp.name;
    return this.selectedExperiences.findIndex(e => (this.expId(e.exp) || e.exp.name) === expKey) > -1;
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

  // Re-chequeo de disponibilidad contra el servidor justo antes de crear la reserva (L1).
  // El snapshot del calendario puede estar desactualizado si otro huésped reservó mientras tanto.
  private ensureStillAvailable(): Promise<boolean> {
    const sId = this.suite?.id || this.suite?._id;
    const currentSlug = this.suite?.slug;
    if (!sId && !currentSlug) return Promise.resolve(true);
    return new Promise(resolve => {
      this.api.get<any>('/availability', {
        checkIn: this.checkIn,
        checkOut: this.checkOut,
        guests: Number(this.guests)
      }).subscribe({
        next: (res) => {
          const data = res?.data || res;
          const available: any[] = data?.availableSuites || [];
          if (!Array.isArray(available) || available.length === 0) {
            // Si la consulta no devuelve suites o el backend no tiene inventario cargado,
            // no bloqueamos preventivamente aquí: el backend validará atómicamente al reservar.
            resolve(true);
            return;
          }
          const aliases: Record<string, string> = {
            'suite-presidencial-mar': 'penthouse-bocagrande',
            'casona-colonial-getsemani': 'suite-arte-patio-getsemani',
            'loft-getsemani-art': 'habitacion-bohemia-getsemani',
            'suite-colonial-patio': 'suite-colonial-centro',
            'villa-playa-boquilla': 'master-villa-boquilla'
          };
          const targetSlug = currentSlug ? (aliases[currentSlug] || currentSlug) : null;
          const stillThere = available.some((a: any) => {
            const s = a?.suite || a;
            const candId = s?.id || s?._id;
            const candSlug = s?.slug ? (aliases[s.slug] || s.slug) : null;
            return (candId && candId === sId) || (targetSlug && candSlug && targetSlug === candSlug);
          });
          resolve(stillThere);
        },
        error: () => resolve(true) // si el chequeo falla, dejamos pasar: el backend valida al crear
      });
    });
  }

  isCorporateRole(): boolean {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    return role === 'host' || role === 'anfitrion' || role === 'admin';
  }

  isHost(): boolean {
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    return role === 'host' || role === 'anfitrion';
  }

  isAdmin(): boolean {
    return (this.auth.currentUser()?.role || '').toLowerCase() === 'admin';
  }

  createBooking(): void {
    if (!this.auth.isLoggedIn()) {
      this.message = 'Debes iniciar sesión para confirmar la reserva.';
      this.isError = true;
      return;
    }

    if (this.isCorporateRole()) {
      this.message = 'Las cuentas de Anfitrión y Administrador tienen un perfil corporativo y no pueden realizar reservas de hospedaje.';
      this.isError = true;
      return;
    }

    if (this.isOwnSuite) {
      this.message = 'Esta suite pertenece a tu propio alojamiento. Gestiónala desde tu panel de anfitrión.';
      this.isError = true;
      return;
    }

    this.bookingBusy = true;
    this.message = '';
    this.availabilityConflict = '';

    this.ensureStillAvailable().then(ok => {
      if (!ok) {
        this.bookingBusy = false;
        this.availabilityConflict = 'Alguien reservó estas fechas hace un momento. Elige otras fechas en el paso 1.';
        this.message = this.availabilityConflict;
        this.isError = true;
        this.goToStep(1);
        this.cdr.detectChanges();
        return;
      }
      this.submitBooking();
    });
  }

  private submitBooking(): void {
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
      experiences: this.selectedExperiences.map(e => this.expId(e.exp)),
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
        const msg: string = err?.error?.message || '';
        const isConflict = /ocupad|disponib|conflict|no se puede reservar|ya está reservad/i.test(msg) || err?.status === 409;
        this.message = isConflict
          ? 'Alguien reservó estas fechas hace un momento. Elige otras fechas en el paso 1.'
          : (msg || 'Error al procesar la reserva.');
        this.isError = true;
        if (isConflict) {
          this.availabilityConflict = this.message;
          this.goToStep(1);
        }
        this.cdr.detectChanges();
      }
    });
  }
}
