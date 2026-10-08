package co.palaciodelmar.backend.config;

import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final BranchRepository branchRepository;
    private final SuiteRepository suiteRepository;
    private final ExperienceRepository experienceRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Branch b1, b2, b3, b4;
        if (branchRepository.count() == 0) {

            // 1. Branches with location coordinates
            b1 = branchRepository.save(Branch.builder()
                    .name("Palacio del Mar · Centro Histórico")
                    .slug("centro-historico")
                    .zone("Centro Histórico")
                    .tagline("Dentro de la ciudad amurallada, a pasos de todo")
                    .description("Casona colonial restaurada dentro de las murallas. Calles empedradas, plazas con vida y los principales monumentos a pocos minutos.")
                    .address("Calle de la Factoría, Centro Histórico, Cartagena")
                    .location(new Branch.GeoLocation(10.4236, -75.5515))
                    .highlights(List.of(
                            new Branch.PointOfInterest("Plaza Santo Domingo", "plaza", 3, new Branch.GeoLocation(10.4231, -75.5512)),
                            new Branch.PointOfInterest("Torre del Reloj", "monument", 5, new Branch.GeoLocation(10.4225, -75.5492))
                    ))
                    .mainImage("https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&auto=format&fit=crop&q=80")
                    .vibe(List.of("histórico", "romántico", "caminable"))
                    .active(true)
                    .order(1)
                    .build());

            b2 = branchRepository.save(Branch.builder()
                    .name("Palacio del Mar · Getsemaní")
                    .slug("getsemani")
                    .zone("Getsemaní")
                    .tagline("Arte callejero, música y la mejor vida nocturna")
                    .description("Casa de barrio con patio, en el sector más colorido y vivo de la ciudad.")
                    .address("Calle del Arsenal, Getsemaní, Cartagena")
                    .location(new Branch.GeoLocation(10.4208, -75.5468))
                    .highlights(List.of(
                            new Branch.PointOfInterest("Plaza de la Trinidad", "plaza", 2, new Branch.GeoLocation(10.4202, -75.5461)),
                            new Branch.PointOfInterest("Calle del Arsenal", "nightlife", 1, new Branch.GeoLocation(10.4210, -75.5470))
                    ))
                    .mainImage("https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80")
                    .vibe(List.of("artístico", "nocturno", "bohemio"))
                    .active(true)
                    .order(2)
                    .build());

            b3 = branchRepository.save(Branch.builder()
                    .name("Palacio del Mar · Bocagrande")
                    .slug("bocagrande")
                    .zone("Bocagrande")
                    .tagline("Frente a la playa, con restaurantes y compras")
                    .description("Edificio frente al mar en la zona hotelera moderna: playa a la puerta.")
                    .address("Avenida San Martín, Bocagrande, Cartagena")
                    .location(new Branch.GeoLocation(10.4042, -75.5574))
                    .highlights(List.of(
                            new Branch.PointOfInterest("Playa Bocagrande", "beach", 1, new Branch.GeoLocation(10.4045, -75.5580)),
                            new Branch.PointOfInterest("Plaza Bocagrande Mall", "shopping", 4, new Branch.GeoLocation(10.4080, -75.5540))
                    ))
                    .mainImage("https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80")
                    .vibe(List.of("playero", "familiar", "moderno"))
                    .active(true)
                    .order(3)
                    .build());

            b4 = branchRepository.save(Branch.builder()
                    .name("Palacio del Mar · La Boquilla")
                    .slug("la-boquilla")
                    .zone("La Boquilla")
                    .tagline("Mar, manglares y tranquilidad al norte")
                    .description("Refugio frente a la playa y la ciénaga, lejos del bullicio.")
                    .address("Vía al Mar, La Boquilla, Cartagena")
                    .location(new Branch.GeoLocation(10.4725, -75.5028))
                    .highlights(List.of(
                            new Branch.PointOfInterest("Manglares de la Boquilla", "nature", 5, new Branch.GeoLocation(10.4735, -75.5015)),
                            new Branch.PointOfInterest("Ciénaga de la Virgen", "nature", 10, new Branch.GeoLocation(10.4750, -75.4950))
                    ))
                    .mainImage("https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80")
                    .vibe(List.of("tranquilo", "naturaleza", "playero"))
                    .active(true)
                    .order(4)
                    .build());
        } else {
            List<Branch> branches = branchRepository.findAll();
            b1 = branches.size() > 0 ? branches.get(0) : null;
            b2 = branches.size() > 1 ? branches.get(1) : b1;
            b3 = branches.size() > 2 ? branches.get(2) : b1;
            b4 = branches.size() > 3 ? branches.get(3) : b1;
        }

        if (suiteRepository.count() == 0 && b1 != null) {
            suiteRepository.save(Suite.builder()
                    .branch(b1.getId())
                    .name("Superior Patio Colonial")
                    .slug("superior-patio-centro")
                    .type("Habitación")
                    .description("Acogedora habitación con jardín colonial privado, ideal para una estancia relajante.")
                    .shortDescription("Habitación acogedora con jardín colonial privado")
                    .basePrice(BigDecimal.valueOf(860000))
                    .originalPrice(BigDecimal.valueOf(1050000))
                    .mainImage("https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Jardín privado", "Aire acondicionado", "Minibar", "WiFi gratuito", "Caja fuerte"))
                    .hasBalcony(false)
                    .hasTerrace(false)
                    .hasJacuzzi(false)
                    .view("garden")
                    .maxGuests(2)
                    .size(42)
                    .available(true)
                    .featured(false)
                    .order(1)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b1.getId())
                    .name("Suite Colonial Imperial")
                    .slug("suite-colonial-centro")
                    .type("Suite Deluxe")
                    .description("Suite histórica con patio colonial, arcos originales y tina hidromasaje.")
                    .shortDescription("Suite histórica con patio colonial y tina")
                    .basePrice(BigDecimal.valueOf(1310000))
                    .originalPrice(BigDecimal.valueOf(1650000))
                    .mainImage("https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Patio colonial", "Tina hidromasaje", "Balcón", "WiFi gratuito", "Desayuno incluido"))
                    .hasBalcony(true)
                    .hasTerrace(false)
                    .hasJacuzzi(true)
                    .view("city")
                    .maxGuests(2)
                    .size(65)
                    .available(true)
                    .featured(true)
                    .order(2)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b1.getId())
                    .name("Suite Vista Murallas")
                    .slug("suite-vista-murallas-centro")
                    .type("Suite Premium")
                    .description("Espaciosa suite con vista privilegiada a las murallas coloniales y jacuzzi privado.")
                    .shortDescription("Suite espaciosa con vista a murallas y jacuzzi")
                    .basePrice(BigDecimal.valueOf(1720000))
                    .originalPrice(BigDecimal.valueOf(2150000))
                    .mainImage("https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1598928506311-c55ded91a20c?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Vista a murallas", "Jacuzzi", "Balcón privado", "Servicio a la habitación"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("city")
                    .maxGuests(2)
                    .size(85)
                    .available(true)
                    .featured(true)
                    .order(3)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b1.getId())
                    .name("Gran Suite Presidencial")
                    .slug("suite-presidencial-centro")
                    .type("Suite Presidencial")
                    .description("La máxima expresión de lujo colonial. Terraza privada de 80 m² con jacuzzi y servicio de mayordomo.")
                    .shortDescription("Lujo máximo con terraza privada y mayordomo 24/7")
                    .basePrice(BigDecimal.valueOf(2780000))
                    .originalPrice(BigDecimal.valueOf(3440000))
                    .mainImage("https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1584132967334-10e028bd69f7?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1616046229478-9901c5536a45?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Terraza privada", "Jacuzzi", "Mayordomo 24/7", "Sala de estar", "Bar privado"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("city")
                    .maxGuests(4)
                    .size(180)
                    .available(true)
                    .featured(true)
                    .order(4)
                    .build());

            // 3. Suites for Getsemaní (b2)
            suiteRepository.save(Suite.builder()
                    .branch(b2.getId())
                    .name("Habitación Bohemia")
                    .slug("habitacion-bohemia-getsemani")
                    .type("Habitación")
                    .description("Habitación decorada con obras de arte local y acceso a patio bohemio.")
                    .shortDescription("Habitación colorida con espíritu artístico en Getsemaní")
                    .basePrice(BigDecimal.valueOf(730000))
                    .originalPrice(BigDecimal.valueOf(890000))
                    .mainImage("https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Patio bohemio", "Aire acondicionado", "WiFi ultra rápido", "Hamaca"))
                    .hasBalcony(false)
                    .hasTerrace(false)
                    .hasJacuzzi(false)
                    .view("city")
                    .maxGuests(2)
                    .size(40)
                    .available(true)
                    .order(1)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b2.getId())
                    .name("Suite Arte & Patio")
                    .slug("suite-arte-patio-getsemani")
                    .type("Suite Deluxe")
                    .description("Suite con galería de arte contemporáneo independiente y zona de descanso en patio interior.")
                    .shortDescription("Suite con galería de arte y patio privado")
                    .basePrice(BigDecimal.valueOf(1150000))
                    .originalPrice(BigDecimal.valueOf(1390000))
                    .mainImage("https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1616046229478-9901c5536a45?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Colección de arte", "Patio privado", "Minibar gourmet", "Sonido Bluetooth"))
                    .hasBalcony(false)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("garden")
                    .maxGuests(2)
                    .size(58)
                    .available(true)
                    .featured(true)
                    .order(2)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b2.getId())
                    .name("Suite Balcón Arsenal")
                    .slug("suite-balcon-getsemani")
                    .type("Suite Premium")
                    .description("Amplia suite sobre la vibrante Calle del Arsenal, con balcón francés y tina hidromasaje.")
                    .shortDescription("Suite con balcón francés a la Calle del Arsenal y tina")
                    .basePrice(BigDecimal.valueOf(1480000))
                    .originalPrice(BigDecimal.valueOf(1820000))
                    .mainImage("https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Balcón colonial", "Tina hidromasaje", "Aislamiento acústico", "WiFi de alta velocidad"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("city")
                    .maxGuests(3)
                    .size(72)
                    .available(true)
                    .featured(true)
                    .order(3)
                    .build());

            // 4. Suites for Bocagrande (b3)
            suiteRepository.save(Suite.builder()
                    .branch(b3.getId())
                    .name("Executive Ocean View")
                    .slug("executive-ocean-bocagrande")
                    .type("Habitación")
                    .description("Moderna habitación con ventanales de piso a techo y vista frontal al Mar Caribe.")
                    .shortDescription("Habitación moderna con vista panóramica al Mar Caribe")
                    .basePrice(BigDecimal.valueOf(920000))
                    .originalPrice(BigDecimal.valueOf(1120000))
                    .mainImage("https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Vista al mar", "Escritorio de trabajo", "Smart TV 65\"", "Máquina Nespresso"))
                    .hasBalcony(true)
                    .hasTerrace(false)
                    .hasJacuzzi(false)
                    .view("ocean")
                    .maxGuests(2)
                    .size(45)
                    .available(true)
                    .order(1)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b3.getId())
                    .name("Deluxe Sky Suite")
                    .slug("deluxe-sky-bocagrande")
                    .type("Suite Deluxe")
                    .description("Ubicada en pisos altos con terraza panorámica sobre la bahía y el océano.")
                    .shortDescription("Suite en piso alto con terraza panorámica al atardecer")
                    .basePrice(BigDecimal.valueOf(1550000))
                    .originalPrice(BigDecimal.valueOf(1900000))
                    .mainImage("https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Terraza vista al mar", "Jacuzzi", "King Bed", "Acceso a Lounge"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("ocean")
                    .maxGuests(2)
                    .size(75)
                    .available(true)
                    .featured(true)
                    .order(2)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b3.getId())
                    .name("Penthouse Ocean Front")
                    .slug("penthouse-bocagrande")
                    .type("Suite Presidencial")
                    .description("Penthouse exclusivo de dos niveles con piscina privada infiniti y vista 360 al Caribe.")
                    .shortDescription("Penthouse de lujo con piscina privada infiniti y vista 360")
                    .basePrice(BigDecimal.valueOf(2450000))
                    .originalPrice(BigDecimal.valueOf(3100000))
                    .mainImage("https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1584132967334-10e028bd69f7?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Piscina privada", "Vista 360", "Mayordomo privado", "Chef bajo solicitud"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("ocean")
                    .maxGuests(4)
                    .size(160)
                    .available(true)
                    .featured(true)
                    .order(3)
                    .build());

            // 5. Suites for La Boquilla (b4)
            suiteRepository.save(Suite.builder()
                    .branch(b4.getId())
                    .name("Bungalow Caribe")
                    .slug("bungalow-caribe-boquilla")
                    .type("Habitación")
                    .description("Bungalow a pocos pasos de la playa de arena suave y palmeras.")
                    .shortDescription("Bungalow frente a la playa con terraza y hamacas")
                    .basePrice(BigDecimal.valueOf(680000))
                    .originalPrice(BigDecimal.valueOf(820000))
                    .mainImage("https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Salida directa a playa", "Hamacas", "Climatización", "Ducha exterior"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(false)
                    .view("ocean")
                    .maxGuests(2)
                    .size(48)
                    .available(true)
                    .order(1)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b4.getId())
                    .name("Suite Eco Manglar")
                    .slug("suite-eco-manglar-boquilla")
                    .type("Suite Deluxe")
                    .description("Refugio rodeado de naturaleza con tina exterior de deck de madera sobre la ciénaga.")
                    .shortDescription("Suite ecológica con tina al aire libre sobre la ciénaga")
                    .basePrice(BigDecimal.valueOf(1080000))
                    .originalPrice(BigDecimal.valueOf(1350000))
                    .mainImage("https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Tina al aire libre", "Deck de madera", "Kayak incluido", "Desayuno artesanal"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("partial_ocean")
                    .maxGuests(2)
                    .size(68)
                    .available(true)
                    .featured(true)
                    .order(2)
                    .build());

            suiteRepository.save(Suite.builder()
                    .branch(b4.getId())
                    .name("Master Villa Beachfront")
                    .slug("master-villa-boquilla")
                    .type("Suite Presidencial")
                    .description("Villa privada independiente con piscina privada, jardín de cocoteros y acceso directo al mar.")
                    .shortDescription("Villa costera privada con piscina y acceso directo al mar")
                    .basePrice(BigDecimal.valueOf(1980000))
                    .originalPrice(BigDecimal.valueOf(2500000))
                    .mainImage("https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&auto=format&fit=crop&q=80")
                    .images(List.of(
                            "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&auto=format&fit=crop&q=80",
                            "https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80"
                    ))
                    .amenities(List.of("Piscina privada", "Jardín tropical", "Cocina equipada", "Servicio de playa privado"))
                    .hasBalcony(true)
                    .hasTerrace(true)
                    .hasJacuzzi(true)
                    .view("ocean")
                    .maxGuests(4)
                    .size(140)
                    .available(true)
                    .featured(true)
                    .order(3)
                    .build());
        }

        if (experienceRepository.count() == 0) {
            // 6. Experiences
            experienceRepository.save(Experience.builder()
                    .name("Islas del Rosario")
                    .shortDescription("Excursión a las Islas del Rosario con snorkel y almuerzo")
                    .description("Navegación privada a las cristalinas aguas del Parque Natural Nacional Rosario.")
                    .price(BigDecimal.valueOf(348000))
                    .category("aventura")
                    .image("https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Atardecer en la Muralla")
                    .shortDescription("Atardecer exclusivo en la muralla con coctel y ron artesanal")
                    .description("Cóctel al atardecer sobre la muralla colonial con vistas al Caribe.")
                    .price(BigDecimal.valueOf(184000))
                    .category("cultural")
                    .image("https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Ruta del Sabor")
                    .shortDescription("Tour gastronómico por mercados locales con chef")
                    .description("Tour gastronómico por mercados locales con nuestro chef ejecutivo.")
                    .price(BigDecimal.valueOf(266000))
                    .category("gastronomía")
                    .image("https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Noche de Palenque")
                    .shortDescription("Noche cultural con música y baile de Palenque")
                    .description("Noche cultural con músicos de Palenque, baile de mapalé y cena en terraza.")
                    .price(BigDecimal.valueOf(389000))
                    .category("cultural")
                    .image("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Catamarán al Atardecer")
                    .shortDescription("Navegación en catamarán de lujo por la bahía al atardecer")
                    .description("Disfruta de la brisa marina, barra libre premium y música en vivo navegando por la bahía de Cartagena.")
                    .price(BigDecimal.valueOf(290000))
                    .category("aventura")
                    .image("https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Taller de Gastronomía Caribeña")
                    .shortDescription("Clase privada de cocina costera con mariscos y coco")
                    .description("Aprende a preparar cazuela de mariscos, arroz con coco y patacones guiado por nuestro chef galardonado.")
                    .price(BigDecimal.valueOf(220000))
                    .category("gastronomía")
                    .image("https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Ritual Spa & Masaje de Oro")
                    .shortDescription("Masaje de piedras calientes y tratamiento facial hidratante")
                    .description("Experiencia holística de relajación de 90 minutos con aceites esenciales y mascarilla botánica.")
                    .price(BigDecimal.valueOf(310000))
                    .category("relajación")
                    .image("https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());

            experienceRepository.save(Experience.builder()
                    .name("Catación de Café & Ron Premium")
                    .shortDescription("Cata guiada de los mejores rones colombianos y cafés de origen")
                    .description("Un viaje sensorial maridando chocolates artesanales, rones añejos y los granos de café más selectos del país.")
                    .price(BigDecimal.valueOf(195000))
                    .category("cultural")
                    .image("https://images.unsplash.com/photo-1511920170033-f8396924c348?w=800&auto=format&fit=crop&q=80")
                    .mainImage("https://images.unsplash.com/photo-1511920170033-f8396924c348?w=800&auto=format&fit=crop&q=80")
                    .active(true)
                    .available(true)
                    .build());
        }

        if (userRepository.findByEmail("admin@palaciodelmar.co").isEmpty()) {
            User admin = User.builder()
                    .name("Admin")
                    .lastName("Palacio")
                    .email("admin@palaciodelmar.co")
                    .password(passwordEncoder.encode("Admin123456!"))
                    .role("admin")
                    .status("active")
                    .emailVerified(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            userRepository.save(admin);
        }
    }
}
