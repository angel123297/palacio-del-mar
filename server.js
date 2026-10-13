import express from 'express';
import cors from 'cors';
import cookieParser from 'cookie-parser';
import jwt from 'jsonwebtoken';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import { execSync } from 'child_process';
import dotenv from 'dotenv';
import { GoogleGenAI } from '@google/genai';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'palacio_del_mar_jwt_secret_key_2026';

app.use(cors({ origin: true, credentials: true }));
app.use(express.json());
app.use(cookieParser());

// --- IN-MEMORY DATABASE & SEED DATA ---
const branches = [
  {
    id: 'branch-1',
    _id: 'branch-1',
    name: 'Palacio del Mar · Centro Histórico',
    slug: 'centro-historico',
    zone: 'Centro Histórico',
    tagline: 'Dentro de la ciudad amurallada, a pasos de todo',
    description: 'Casona colonial restaurada dentro de las murallas. Calles empedradas, plazas con vida y los principales monumentos a pocos minutos.',
    address: 'Calle de la Factoría, Centro Histórico, Cartagena',
    location: { lat: 10.4236, lng: -75.5515 },
    highlights: [
      { name: 'Plaza Santo Domingo', type: 'plaza', walkingMinutes: 3, location: { lat: 10.4231, lng: -75.5512 } },
      { name: 'Torre del Reloj', type: 'monument', walkingMinutes: 5, location: { lat: 10.4225, lng: -75.5492 } }
    ],
    mainImage: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&auto=format&fit=crop&q=80',
    vibe: ['histórico', 'romántico', 'caminable'],
    active: true,
    order: 1
  },
  {
    id: 'branch-2',
    _id: 'branch-2',
    name: 'Palacio del Mar · Getsemaní',
    slug: 'getsemani',
    zone: 'Getsemaní',
    tagline: 'Arte callejero, música y la mejor vida nocturna',
    description: 'Casa de barrio con patio, en el sector más colorido y vivo de la ciudad.',
    address: 'Calle del Arsenal, Getsemaní, Cartagena',
    location: { lat: 10.4208, lng: -75.5468 },
    highlights: [
      { name: 'Plaza de la Trinidad', type: 'plaza', walkingMinutes: 2, location: { lat: 10.4202, lng: -75.5461 } },
      { name: 'Calle del Arsenal', type: 'nightlife', walkingMinutes: 1, location: { lat: 10.4210, lng: -75.5470 } }
    ],
    mainImage: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80',
    vibe: ['artístico', 'nocturno', 'bohemio'],
    active: true,
    order: 2
  },
  {
    id: 'branch-3',
    _id: 'branch-3',
    name: 'Palacio del Mar · Bocagrande',
    slug: 'bocagrande',
    zone: 'Bocagrande',
    tagline: 'Frente a la playa, con restaurantes y compras',
    description: 'Edificio frente al mar en la zona hotelera moderna: playa a la puerta.',
    address: 'Avenida San Martín, Bocagrande, Cartagena',
    location: { lat: 10.4042, lng: -75.5574 },
    highlights: [
      { name: 'Playa Bocagrande', type: 'beach', walkingMinutes: 1, location: { lat: 10.4045, lng: -75.5580 } },
      { name: 'Plaza Bocagrande Mall', type: 'shopping', walkingMinutes: 4, location: { lat: 10.4080, lng: -75.5540 } }
    ],
    mainImage: 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80',
    vibe: ['playero', 'familiar', 'moderno'],
    active: true,
    order: 3
  },
  {
    id: 'branch-4',
    _id: 'branch-4',
    name: 'Palacio del Mar · La Boquilla',
    slug: 'la-boquilla',
    zone: 'La Boquilla',
    tagline: 'Mar, manglares y tranquilidad al norte',
    description: 'Refugio frente a la playa y la ciénaga, lejos del bullicio.',
    address: 'Vía al Mar, La Boquilla, Cartagena',
    location: { lat: 10.4725, lng: -75.5028 },
    highlights: [
      { name: 'Manglares de la Boquilla', type: 'nature', walkingMinutes: 5, location: { lat: 10.4735, lng: -75.5015 } },
      { name: 'Ciénaga de la Virgen', type: 'nature', walkingMinutes: 10, location: { lat: 10.4750, lng: -75.4950 } }
    ],
    mainImage: 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80',
    vibe: ['tranquilo', 'naturaleza', 'playero'],
    active: true,
    order: 4
  }
];

const suites = [
  // Centro Histórico
  {
    id: 'suite-1',
    _id: 'suite-1',
    branchId: 'branch-1',
    branch: 'branch-1',
    name: 'Superior Patio Colonial',
    slug: 'superior-patio-centro',
    type: 'Habitación',
    description: 'Acogedora habitación con jardín colonial privado, ideal para una estancia relajante.',
    shortDescription: 'Habitación acogedora con jardín colonial privado',
    basePrice: 860000,
    pricePerNight: 860000,
    originalPrice: 1050000,
    mainImage: 'https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80',
    images: [
      'https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80'
    ],
    amenities: ['Jardín privado', 'Aire acondicionado', 'Minibar', 'WiFi gratuito', 'Caja fuerte'],
    hasBalcony: false,
    hasTerrace: false,
    hasJacuzzi: false,
    view: 'garden',
    maxGuests: 2,
    totalUnits: 2,
    size: 42,
    available: true,
    isAvailable: true,
    featured: false,
    order: 1
  },
  {
    id: 'suite-2',
    _id: 'suite-2',
    branchId: 'branch-1',
    branch: 'branch-1',
    name: 'Suite Colonial Imperial',
    slug: 'suite-colonial-centro',
    type: 'Suite Deluxe',
    description: 'Suite histórica con patio colonial, arcos originales y tina hidromasaje.',
    shortDescription: 'Suite histórica con patio colonial y tina',
    basePrice: 1310000,
    pricePerNight: 1310000,
    originalPrice: 1650000,
    mainImage: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80',
    images: [
      'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?w=800&auto=format&fit=crop&q=80'
    ],
    amenities: ['Patio colonial', 'Tina hidromasaje', 'Balcón', 'WiFi gratuito', 'Desayuno incluido'],
    hasBalcony: true,
    hasTerrace: false,
    hasJacuzzi: true,
    view: 'city',
    maxGuests: 2,
    totalUnits: 2,
    size: 65,
    available: true,
    isAvailable: true,
    featured: true,
    order: 2
  },
  {
    id: 'suite-3',
    _id: 'suite-3',
    branchId: 'branch-1',
    branch: 'branch-1',
    name: 'Suite Vista Murallas',
    slug: 'suite-vista-murallas-centro',
    type: 'Suite Premium',
    description: 'Espaciosa suite con vista privilegiada a las murallas coloniales y jacuzzi privado.',
    shortDescription: 'Suite espaciosa con vista a murallas y jacuzzi',
    basePrice: 1720000,
    pricePerNight: 1720000,
    originalPrice: 2150000,
    mainImage: 'https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800&auto=format&fit=crop&q=80',
    images: [
      'https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1598928506311-c55ded91a20c?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80'
    ],
    amenities: ['Vista a murallas', 'Jacuzzi', 'Balcón privado', 'Servicio a la habitación'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'city',
    maxGuests: 3,
    totalUnits: 2,
    size: 85,
    available: true,
    isAvailable: true,
    featured: true,
    order: 3
  },
  {
    id: 'suite-4',
    _id: 'suite-4',
    branchId: 'branch-1',
    branch: 'branch-1',
    name: 'Gran Suite Presidencial',
    slug: 'suite-presidencial-centro',
    type: 'Suite Presidencial',
    description: 'La máxima expresión de lujo colonial. Terraza privada de 80 m² con jacuzzi y servicio de mayordomo.',
    shortDescription: 'Lujo máximo con terraza privada y mayordomo 24/7',
    basePrice: 2780000,
    pricePerNight: 2780000,
    originalPrice: 3440000,
    mainImage: 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80',
    images: [
      'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1584132967334-10e028bd69f7?w=800&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1616046229478-9901c5536a45?w=800&auto=format&fit=crop&q=80'
    ],
    amenities: ['Terraza privada', 'Jacuzzi', 'Mayordomo 24/7', 'Sala de estar', 'Bar privado'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'city',
    maxGuests: 4,
    totalUnits: 1,
    size: 180,
    available: true,
    isAvailable: true,
    featured: true,
    order: 4
  },
  // Getsemaní
  {
    id: 'suite-5',
    _id: 'suite-5',
    branchId: 'branch-2',
    branch: 'branch-2',
    name: 'Habitación Bohemia',
    slug: 'habitacion-bohemia-getsemani',
    type: 'Habitación',
    description: 'Habitación decorada con obras de arte local y acceso a patio bohemio.',
    shortDescription: 'Habitación colorida con espíritu artístico en Getsemaní',
    basePrice: 730000,
    pricePerNight: 730000,
    originalPrice: 890000,
    mainImage: 'https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Patio bohemio', 'Aire acondicionado', 'WiFi ultra rápido', 'Hamaca'],
    hasBalcony: false,
    hasTerrace: false,
    hasJacuzzi: false,
    view: 'city',
    maxGuests: 2,
    totalUnits: 2,
    size: 40,
    available: true,
    isAvailable: true,
    order: 1
  },
  {
    id: 'suite-6',
    _id: 'suite-6',
    branchId: 'branch-2',
    branch: 'branch-2',
    name: 'Suite Arte & Patio',
    slug: 'suite-arte-patio-getsemani',
    type: 'Suite Deluxe',
    description: 'Suite con galería de arte contemporáneo independiente y zona de descanso en patio interior.',
    shortDescription: 'Suite con galería de arte y patio privado',
    basePrice: 1150000,
    pricePerNight: 1150000,
    originalPrice: 1390000,
    mainImage: 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Colección de arte', 'Patio privado', 'Minibar gourmet', 'Sonido Bluetooth'],
    hasBalcony: false,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'garden',
    maxGuests: 2,
    totalUnits: 2,
    size: 58,
    available: true,
    isAvailable: true,
    featured: true,
    order: 2
  },
  {
    id: 'suite-7',
    _id: 'suite-7',
    branchId: 'branch-2',
    branch: 'branch-2',
    name: 'Suite Balcón Arsenal',
    slug: 'suite-balcon-getsemani',
    type: 'Suite Premium',
    description: 'Amplia suite sobre la vibrante Calle del Arsenal, con balcón francés y tina hidromasaje.',
    shortDescription: 'Suite con balcón francés a la Calle del Arsenal y tina',
    basePrice: 1480000,
    pricePerNight: 1480000,
    originalPrice: 1820000,
    mainImage: 'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Balcón colonial', 'Tina hidromasaje', 'Aislamiento acústico', 'WiFi de alta velocidad'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'city',
    maxGuests: 3,
    totalUnits: 2,
    size: 72,
    available: true,
    isAvailable: true,
    featured: true,
    order: 3
  },
  // Bocagrande
  {
    id: 'suite-8',
    _id: 'suite-8',
    branchId: 'branch-3',
    branch: 'branch-3',
    name: 'Executive Ocean View',
    slug: 'executive-ocean-bocagrande',
    type: 'Habitación',
    description: 'Moderna habitación con ventanales de piso a techo y vista frontal al Mar Caribe.',
    shortDescription: 'Habitación moderna con vista panóramica al Mar Caribe',
    basePrice: 920000,
    pricePerNight: 920000,
    originalPrice: 1120000,
    mainImage: 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Vista al mar', 'Escritorio de trabajo', 'Smart TV 65"', 'Máquina Nespresso'],
    hasBalcony: true,
    hasTerrace: false,
    hasJacuzzi: false,
    view: 'ocean',
    maxGuests: 2,
    totalUnits: 2,
    size: 45,
    available: true,
    isAvailable: true,
    order: 1
  },
  {
    id: 'suite-9',
    _id: 'suite-9',
    branchId: 'branch-3',
    branch: 'branch-3',
    name: 'Deluxe Sky Suite',
    slug: 'deluxe-sky-bocagrande',
    type: 'Suite Deluxe',
    description: 'Ubicada en pisos altos con terraza panorámica sobre la bahía y el océano.',
    shortDescription: 'Suite en piso alto con terraza panorámica al atardecer',
    basePrice: 1550000,
    pricePerNight: 1550000,
    originalPrice: 1900000,
    mainImage: 'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Terraza vista al mar', 'Jacuzzi', 'King Bed', 'Acceso a Lounge'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'ocean',
    maxGuests: 2,
    totalUnits: 2,
    size: 75,
    available: true,
    isAvailable: true,
    featured: true,
    order: 2
  },
  {
    id: 'suite-10',
    _id: 'suite-10',
    branchId: 'branch-3',
    branch: 'branch-3',
    name: 'Penthouse Ocean Front',
    slug: 'penthouse-bocagrande',
    type: 'Suite Presidencial',
    description: 'Penthouse exclusivo de dos niveles con piscina privada infiniti al Caribe.',
    shortDescription: 'Penthouse de lujo con piscina privada infiniti',
    basePrice: 2450000,
    pricePerNight: 2450000,
    originalPrice: 3100000,
    mainImage: 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Piscina privada', 'Mayordomo privado', 'Chef bajo solicitud'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'ocean',
    maxGuests: 4,
    totalUnits: 1,
    size: 160,
    available: true,
    isAvailable: true,
    featured: true,
    order: 3
  },
  // La Boquilla
  {
    id: 'suite-11',
    _id: 'suite-11',
    branchId: 'branch-4',
    branch: 'branch-4',
    name: 'Bungalow Caribe',
    slug: 'bungalow-caribe-boquilla',
    type: 'Habitación',
    description: 'Bungalow a pocos pasos de la playa de arena suave y palmeras.',
    shortDescription: 'Bungalow frente a la playa con terraza y hamacas',
    basePrice: 680000,
    pricePerNight: 680000,
    originalPrice: 820000,
    mainImage: 'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Salida directa a playa', 'Hamacas', 'Climatización', 'Ducha exterior'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: false,
    view: 'ocean',
    maxGuests: 2,
    totalUnits: 2,
    size: 48,
    available: true,
    isAvailable: true,
    order: 1
  },
  {
    id: 'suite-12',
    _id: 'suite-12',
    branchId: 'branch-4',
    branch: 'branch-4',
    name: 'Master Villa Beachfront',
    slug: 'master-villa-boquilla',
    type: 'Suite Presidencial',
    description: 'Villa privada independiente con piscina privada, jardín de cocoteros y acceso directo al mar.',
    shortDescription: 'Villa costera privada con piscina y acceso directo al mar',
    basePrice: 1980000,
    pricePerNight: 1980000,
    originalPrice: 2500000,
    mainImage: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Piscina privada', 'Jardín tropical', 'Cocina equipada', 'Servicio de playa privado'],
    hasBalcony: true,
    hasTerrace: true,
    hasJacuzzi: true,
    view: 'ocean',
    maxGuests: 4,
    totalUnits: 1,
    size: 140,
    available: true,
    isAvailable: true,
    featured: true,
    order: 2
  }
];

const experiences = [
  {
    id: 'exp-1',
    _id: 'exp-1',
    name: 'Islas del Rosario',
    title: 'Islas del Rosario',
    shortDescription: 'Excursión a las Islas del Rosario con snorkel y almuerzo',
    description: 'Navegación privada a las cristalinas aguas del Parque Natural Nacional Rosario.',
    price: 348000,
    category: 'aventura',
    image: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 6
  },
  {
    id: 'exp-2',
    _id: 'exp-2',
    name: 'Atardecer en la Muralla',
    title: 'Atardecer en la Muralla',
    shortDescription: 'Atardecer exclusivo en la muralla con coctel y ron artesanal',
    description: 'Cóctel al atardecer sobre la muralla colonial con vistas al Caribe.',
    price: 184000,
    category: 'cultural',
    image: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 2
  },
  {
    id: 'exp-3',
    _id: 'exp-3',
    name: 'Ruta del Sabor',
    title: 'Ruta del Sabor',
    shortDescription: 'Tour gastronómico por mercados locales con chef',
    description: 'Tour gastronómico por mercados locales con nuestro chef ejecutivo.',
    price: 266000,
    category: 'gastronomía',
    image: 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 3
  },
  {
    id: 'exp-4',
    _id: 'exp-4',
    name: 'Noche de Palenque',
    title: 'Noche de Palenque',
    shortDescription: 'Noche cultural con música y baile de Palenque',
    description: 'Noche cultural con músicos de Palenque, baile de mapalé y cena en terraza.',
    price: 389000,
    category: 'cultural',
    image: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 4
  },
  {
    id: 'exp-5',
    _id: 'exp-5',
    name: 'Catamarán al Atardecer',
    title: 'Catamarán al Atardecer',
    shortDescription: 'Navegación en catamarán de lujo por la bahía al atardecer',
    description: 'Disfruta de la brisa marina, barra libre premium y música en vivo navegando por la bahía de Cartagena.',
    price: 290000,
    category: 'aventura',
    image: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 3
  },
  {
    id: 'exp-6',
    _id: 'exp-6',
    name: 'Taller de Gastronomía Caribeña',
    title: 'Taller de Gastronomía Caribeña',
    shortDescription: 'Clase privada de cocina costera con mariscos y coco',
    description: 'Aprende a preparar cazuela de mariscos, arroz con coco y patacones guiado por nuestro chef galardonado.',
    price: 220000,
    category: 'gastronomía',
    image: 'https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 2.5
  },
  {
    id: 'exp-7',
    _id: 'exp-7',
    name: 'Ritual Spa & Masaje de Oro',
    title: 'Ritual Spa & Masaje de Oro',
    shortDescription: 'Masaje de piedras calientes y tratamiento facial hidratante',
    description: 'Experiencia holística de relajación de 90 minutos con aceites esenciales y mascarilla botánica.',
    price: 310000,
    category: 'relajación',
    image: 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 1.5
  },
  {
    id: 'exp-8',
    _id: 'exp-8',
    name: 'Catación de Café & Ron Premium',
    title: 'Catación de Café & Ron Premium',
    shortDescription: 'Cata guiada de los mejores rones colombianos y cafés de origen',
    description: 'Un viaje sensorial maridando chocolates artesanales, rones añejos y los granos de café más selectos del país.',
    price: 195000,
    category: 'cultural',
    image: 'https://images.unsplash.com/photo-1511920170033-f8396924c348?w=800&auto=format&fit=crop&q=80',
    mainImage: 'https://images.unsplash.com/photo-1511920170033-f8396924c348?w=800&auto=format&fit=crop&q=80',
    active: true,
    available: true,
    durationHours: 2
  }
];

const promotions = [
  {
    id: 'promo-1',
    _id: 'promo-1',
    branch: 'branch-1',
    branchId: 'branch-1',
    code: 'ROMANCE15',
    title: 'Escapada Romántica Cartagena',
    discountPercent: 15,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    active: true,
    isSample: true,
    createdAt: new Date().toISOString()
  },
  {
    id: 'promo-2',
    _id: 'promo-2',
    branch: 'branch-3',
    branchId: 'branch-3',
    code: 'PLAYA20',
    title: 'Verano de Lujo en Bocagrande',
    discountPercent: 20,
    startDate: '2026-06-01',
    endDate: '2026-08-31',
    active: true,
    isSample: true,
    createdAt: new Date().toISOString()
  }
];

const users = [
  {
    id: 'user-admin',
    _id: 'user-admin',
    name: 'Admin',
    lastName: 'Palacio',
    email: 'admin@palaciodelmar.co',
    password: 'Admin123456!',
    role: 'admin',
    status: 'active',
    emailVerified: true,
    createdAt: new Date().toISOString()
  },
  {
    id: 'user-host',
    _id: 'user-host',
    name: 'Anfitrión',
    lastName: 'Palacio',
    email: 'anfitrion@palaciodelmar.co',
    password: 'Host123456!',
    role: 'host',
    status: 'active',
    branches: ['branch-1', 'branch-2', 'branch-3', 'branch-4'],
    emailVerified: true,
    createdAt: new Date().toISOString()
  },
  {
    id: 'user-guest',
    _id: 'user-guest',
    name: 'Huésped',
    lastName: 'Verificado',
    email: 'huesped@palaciodelmar.co',
    password: 'User123456!',
    role: 'user',
    status: 'active',
    emailVerified: true,
    createdAt: new Date().toISOString()
  }
];

const bookings = [
  {
    id: 'bk-1',
    _id: 'bk-1',
    bookingCode: 'PDM-2026-8812',
    userId: 'user-guest',
    user: 'user-guest',
    suiteId: 'suite-2',
    suite: suites[1],
    branch: branches[0],
    branchId: 'branch-1',
    checkIn: '2026-11-15',
    checkOut: '2026-11-18',
    nights: 3,
    guests: 2,
    children: 0,
    totalPrice: 3930000,
    paidAmount: 3930000,
    balanceDue: 0,
    status: 'confirmed',
    paymentStatus: 'paid',
    guestName: 'Huésped Verificado',
    guestEmail: 'huesped@palaciodelmar.co',
    guestPhone: '+57 300 123 4567',
    smartLockPin: '4829',
    preCheckInCompleted: true,
    checkedIn: false,
    checkedOut: false,
    isModifiable: true,
    isCancelable: true,
    notes: 'Habitación en piso alto solicitada.',
    createdAt: new Date().toISOString()
  },
  {
    id: 'bk-2',
    _id: 'bk-2',
    bookingCode: 'PDM-2026-9430',
    userId: 'user-guest',
    user: 'user-guest',
    suiteId: 'suite-3',
    suite: suites[2],
    branch: branches[0],
    branchId: 'branch-1',
    checkIn: '2026-12-20',
    checkOut: '2026-12-24',
    nights: 4,
    guests: 2,
    children: 0,
    totalPrice: 6880000,
    paidAmount: 6880000,
    balanceDue: 0,
    status: 'confirmed',
    paymentStatus: 'paid',
    guestName: 'Huésped Verificado',
    guestEmail: 'huesped@palaciodelmar.co',
    guestPhone: '+57 300 123 4567',
    smartLockPin: '9152',
    preCheckInCompleted: false,
    checkedIn: false,
    checkedOut: false,
    isModifiable: true,
    isCancelable: true,
    notes: 'Aniversario de bodas.',
    createdAt: new Date().toISOString()
  }
];

// Track idempotent payment requests
const idempotentPayments = new Map();

// --- BUSINESS LOGIC HELPERS ---

// 1. Season multiplier evaluation
function getSeasonMultiplier(dateStr) {
  const date = new Date(dateStr);
  const m = date.getMonth() + 1; // 1-12
  const d = date.getDate();
  const dayOfWeek = date.getDay(); // 0 is Sun, 6 is Sat

  // Peak season (Dec 20 - Jan 15, Semana Santa late March / early April)
  if ((m === 12 && d >= 20) || (m === 1 && d <= 15) || (m === 3 && d >= 26) || (m === 4 && d <= 5)) {
    return 1.5;
  }
  // High season (June, July, August, Dec 1-19)
  if (m === 6 || m === 7 || m === 8 || (m === 12 && d < 20)) {
    return 1.3;
  }
  // Mid season (March, November, or weekends Fri/Sat)
  if (m === 3 || m === 11 || dayOfWeek === 5 || dayOfWeek === 6) {
    return 1.15;
  }
  // Low season
  return 1.0;
}

// 2. Comprehensive Pricing Engine
function calculateStayPricing({ suite, checkIn, checkOut, guests = 2, promoCode = '', airportPickup = false, foodOrders = [], experiencesList = [] }) {
  const cIn = new Date(checkIn);
  const cOut = new Date(checkOut);
  const nights = Math.max(1, Math.round((cOut - cIn) / (1000 * 60 * 60 * 24)));

  let roomTotal = 0;
  const nightlyRates = [];

  for (let i = 0; i < nights; i++) {
    const curDate = new Date(cIn.getTime() + i * 86400000);
    const curDateStr = curDate.toISOString().split('T')[0];
    const mult = getSeasonMultiplier(curDateStr);
    const nightlyPrice = Math.round(suite.basePrice * mult);
    nightlyRates.push({ date: curDateStr, multiplier: mult, price: nightlyPrice });
    roomTotal += nightlyPrice;
  }

  // Duration discounts
  let durationDiscountPercent = 0;
  if (nights >= 28) {
    durationDiscountPercent = 10;
  } else if (nights >= 7) {
    durationDiscountPercent = 5;
  }
  const durationDiscountAmount = Math.round(roomTotal * (durationDiscountPercent / 100));
  let discountedRoom = roomTotal - durationDiscountAmount;

  // Extra guest charge ($120.000 COP / night per guest exceeding 2)
  const numGuests = Number(guests) || 2;
  let extraGuestsCharge = 0;
  if (numGuests > 2) {
    extraGuestsCharge = (numGuests - 2) * 120000 * nights;
  }

  // Promotional discount
  let promoDiscountPercent = 0;
  let matchedPromo = null;
  const targetBranch = suite.branchId || suite.branch;

  for (const p of promotions) {
    if (p.active) {
      const matchBranch = !p.branch || p.branch === 'all' || p.branch === targetBranch || p.branchId === targetBranch;
      const matchDates = (!p.startDate || p.startDate <= checkIn) && (!p.endDate || p.endDate >= checkOut);
      const matchCode = promoCode && p.code && p.code.toUpperCase() === promoCode.trim().toUpperCase();
      if ((matchBranch && matchDates) || matchCode) {
        if (p.discountPercent > promoDiscountPercent) {
          promoDiscountPercent = p.discountPercent;
          matchedPromo = p;
        }
      }
    }
  }

  const promoDiscountAmount = Math.round(discountedRoom * (promoDiscountPercent / 100));
  discountedRoom = discountedRoom - promoDiscountAmount;

  // Extra services
  const airportFee = airportPickup ? 150000 : 0;
  const foodTotal = (foodOrders || []).reduce((acc, f) => acc + (f.price || 80000), 0);
  const expTotal = (experiencesList || []).reduce((acc, exp) => acc + (exp.price || 0), 0);

  const subtotal = discountedRoom + extraGuestsCharge + airportFee + foodTotal + expTotal;
  const tax = Math.round(subtotal * 0.19); // 19% IVA
  const totalPrice = subtotal + tax;

  return {
    nights,
    baseNightlyPrice: suite.basePrice,
    nightlyRates,
    roomTotal,
    durationDiscountPercent,
    durationDiscountAmount,
    extraGuestsCharge,
    promoDiscountPercent,
    promoDiscountAmount,
    appliedPromotion: matchedPromo ? matchedPromo.title : null,
    airportFee,
    foodTotal,
    expTotal,
    subtotal,
    tax,
    totalPrice
  };
}

// 3. Inventory overlap verification
function isSuiteAvailableForDates(suiteId, checkIn, checkOut, excludeBookingId = null) {
  const suite = suites.find(s => s.id === suiteId || s._id === suiteId);
  if (!suite) return false;
  if (suite.isAvailable === false || suite.available === false) return false;

  const totalCapacity = suite.totalUnits || (suite.type?.toLowerCase().includes('presidencial') ? 1 : 2);
  const now = new Date();

  // Find all active bookings overlapping [checkIn, checkOut)
  const overlapping = bookings.filter(b => {
    if (excludeBookingId && (b.id === excludeBookingId || b._id === excludeBookingId)) return false;
    if (b.suiteId !== suiteId && b.suite?.id !== suiteId && b.suite?._id !== suiteId) return false;
    if (b.status === 'cancelled' || b.status === 'expired') return false;

    // If pending and unpaid, verify TTL has not expired
    if (b.status === 'pending' && (!b.paidAmount || b.paidAmount === 0) && b.expiresAt && new Date(b.expiresAt) < now) {
      b.status = 'expired';
      return false;
    }

    return b.checkIn < checkOut && b.checkOut > checkIn;
  });

  return overlapping.length < totalCapacity;
}

// 4. Algorithm of Luhn for simulated credit cards
function validateLuhn(cardNumber) {
  const digits = String(cardNumber).replace(/\D/g, '');
  if (digits.length < 13 || digits.length > 19) return false;

  let sum = 0;
  let shouldDouble = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let digit = parseInt(digits.charAt(i), 10);
    if (shouldDouble) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    shouldDouble = !shouldDouble;
  }
  return sum % 10 === 0;
}

function detectCardBrand(digits) {
  if (digits.startsWith('4')) return 'Visa';
  if (/^5[1-5]/.test(digits) || /^2[2-7]/.test(digits)) return 'Mastercard';
  if (/^3[47]/.test(digits)) return 'American Express';
  return 'Tarjeta';
}

// 5. Cleanup expired pending bookings periodically (15-min TTL)
function cleanupExpiredBookings() {
  const now = new Date();
  bookings.forEach(b => {
    if (b.status === 'pending' && (!b.paidAmount || b.paidAmount === 0) && b.expiresAt && new Date(b.expiresAt) < now) {
      b.status = 'expired';
    }
  });
}
setInterval(cleanupExpiredBookings, 60000);

// Helper functions
function findUserByEmail(email) {
  return users.find(u => u.email.toLowerCase() === (email || '').toLowerCase());
}

function findUserById(id) {
  return users.find(u => u.id === id || u._id === id);
}

function sanitizeUser(u) {
  if (!u) return null;
  const { password, ...safe } = u;
  return safe;
}

function authenticate(req, res, next) {
  const token = req.cookies?.jwt || (req.headers.authorization?.startsWith('Bearer ') ? req.headers.authorization.split(' ')[1] : null);
  if (!token) {
    req.user = null;
    return next();
  }
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = findUserById(decoded.id) || null;
  } catch (e) {
    req.user = null;
  }
  next();
}

function requireAuth(req, res, next) {
  if (!req.user) {
    return res.status(401).json({ success: false, message: 'No autenticado' });
  }
  next();
}

app.use(authenticate);

// --- API ROUTES ---

// 1. Branches
app.get('/api/branches', (req, res) => {
  res.json({ success: true, data: branches });
});

app.get('/api/branches/:slug', (req, res) => {
  const branch = branches.find(b => b.slug === req.params.slug || b.id === req.params.slug);
  if (!branch) return res.status(404).json({ success: false, message: 'Sede no encontrada' });
  res.json({ success: true, data: branch });
});

// 2. Suites
app.get('/api/suites', (req, res) => {
  const { branch } = req.query;
  let list = suites.map(s => {
    const bObj = branches.find(b => b.id === (s.branchId || s.branch));
    return { ...s, branch: bObj || s.branch };
  });

  if (branch) {
    const targetBranch = branches.find(b => b.slug === branch || b.id === branch);
    const branchId = targetBranch ? targetBranch.id : branch;
    list = list.filter(s => s.branchId === branchId || s.branch === branchId || s.branch?.id === branchId || s.branch?.slug === branch);
  }
  res.json({ success: true, data: list });
});

app.get('/api/suites/types', (req, res) => {
  const typeMap = new Map();
  for (const s of suites) {
    if (!typeMap.has(s.type)) {
      typeMap.set(s.type, {
        type: s.type,
        count: 0,
        minPrice: s.basePrice,
        image: s.mainImage,
        description: s.description
      });
    }
    const cur = typeMap.get(s.type);
    cur.count++;
    if (s.basePrice < cur.minPrice) cur.minPrice = s.basePrice;
  }
  res.json({ success: true, data: Array.from(typeMap.values()) });
});

app.get('/api/suites/branch/:branchId', (req, res) => {
  const list = suites.filter(s => s.branchId === req.params.branchId || s.branch === req.params.branchId);
  res.json({ success: true, data: list });
});

app.get('/api/suites/:idOrSlug', (req, res) => {
  const suite = suites.find(s => s.id === req.params.idOrSlug || s.slug === req.params.idOrSlug);
  if (!suite) return res.status(404).json({ success: false, message: 'Suite no encontrada' });
  const branch = branches.find(b => b.id === (suite.branchId || suite.branch)) || branches[0];
  res.json({ success: true, data: { ...suite, branch } });
});

app.post('/api/suites/calculate-price', (req, res) => {
  const { suiteId, checkIn, checkOut, guests, promoCode, airportPickup, foodOrders } = req.body;
  const suite = suites.find(s => s.id === suiteId || s.slug === suiteId) || suites[0];

  const pricing = calculateStayPricing({
    suite,
    checkIn: checkIn || new Date().toISOString().split('T')[0],
    checkOut: checkOut || new Date(Date.now() + 86400000 * 2).toISOString().split('T')[0],
    guests: guests || 2,
    promoCode: promoCode || '',
    airportPickup: !!airportPickup,
    foodOrders: foodOrders || []
  });

  res.json({
    success: true,
    data: {
      suiteId: suite.id,
      ...pricing
    }
  });
});

// 3. Experiences
app.get('/api/experiences', (req, res) => {
  res.json({ success: true, data: experiences });
});

// 4. Availability
app.get('/api/availability', (req, res) => {
  const { checkIn, checkOut, branch, suiteType, guests } = req.query;
  const today = new Date().toISOString().split('T')[0];
  const cIn = checkIn && checkIn >= today ? checkIn : today;
  const cOut = checkOut && checkOut > cIn ? checkOut : new Date(Date.now() + 86400000 * 2).toISOString().split('T')[0];

  let list = suites;
  if (branch) {
    const targetBranch = branches.find(b => b.slug === branch || b.id === branch);
    const branchId = targetBranch ? targetBranch.id : branch;
    list = list.filter(s => s.branchId === branchId || s.branch === branchId);
  }
  if (suiteType) {
    list = list.filter(s => s.type.toLowerCase().includes(suiteType.toLowerCase()));
  }
  if (guests) {
    const g = parseInt(guests);
    list = list.filter(s => (s.maxGuests || 2) >= g);
  }

  const availableSuites = [];
  const unavailableSuites = [];

  list.forEach(suite => {
    const isAvail = isSuiteAvailableForDates(suite.id, cIn, cOut);
    const branchObj = branches.find(b => b.id === (suite.branchId || suite.branch)) || branches[0];
    const pricing = calculateStayPricing({ suite, checkIn: cIn, checkOut: cOut, guests: guests ? parseInt(guests) : 2 });

    const item = {
      suite: { ...suite, branch: branchObj },
      isAvailable: isAvail,
      availableUnits: isAvail ? (suite.totalUnits || 2) : 0,
      totalUnits: suite.totalUnits || 2,
      pricePerNight: Math.round(pricing.roomTotal / pricing.nights),
      totalPrice: pricing.totalPrice
    };

    if (isAvail) {
      availableSuites.push(item);
    } else {
      unavailableSuites.push(item);
    }
  });

  const nights = Math.max(1, Math.round((new Date(cOut) - new Date(cIn)) / 86400000));

  res.json({
    success: true,
    data: {
      checkIn: cIn,
      checkOut: cOut,
      nights,
      availableSuites,
      unavailableSuites
    }
  });
});

app.get('/api/availability/monthly', (req, res) => {
  const year = parseInt(req.query.year) || new Date().getFullYear();
  const month = parseInt(req.query.month) || (new Date().getMonth() + 1);
  const targetSuiteId = req.query.suiteId;
  const daysInMonth = new Date(year, month, 0).getDate();
  const calendar = [];
  let soldOutDays = 0;

  for (let day = 1; day <= daysInMonth; day++) {
    const dayStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    const nextDayStr = new Date(new Date(dayStr).getTime() + 86400000).toISOString().split('T')[0];

    let isDayAvailable = true;
    if (targetSuiteId) {
      isDayAvailable = isSuiteAvailableForDates(targetSuiteId, dayStr, nextDayStr);
    } else {
      const anyAvail = suites.some(s => isSuiteAvailableForDates(s.id, dayStr, nextDayStr));
      isDayAvailable = anyAvail;
    }

    if (!isDayAvailable) soldOutDays++;

    calendar.push({
      date: dayStr,
      day,
      totalSuites: suites.length,
      availableSuites: isDayAvailable ? 8 : 0,
      soldOut: !isDayAvailable,
      occupancyRate: isDayAvailable ? 0.35 : 1.0,
      startingPrice: 680000 * getSeasonMultiplier(dayStr)
    });
  }

  res.json({
    success: true,
    data: {
      year,
      month,
      daysInMonth,
      totalSuites: suites.length,
      calendar,
      summary: {
        soldOutDays,
        averageOccupancy: Math.round((soldOutDays / daysInMonth) * 100)
      }
    }
  });
});

app.get(['/api/availability/promotions', '/api/promotions', '/api/admin/promotions'], (req, res) => {
  res.json({ success: true, data: promotions });
});

app.post(['/api/admin/promotions', '/api/host/promotions', '/api/promotions'], (req, res) => {
  const { title, branchId, branch, discountPercent, startDate, endDate, code } = req.body || {};
  if (!title || !startDate || !endDate) {
    return res.status(400).json({ success: false, message: 'Título y rango de fechas requeridos' });
  }

  const discount = Math.min(50, Math.max(5, parseInt(discountPercent) || 15));
  const newPromo = {
    id: `promo-${Date.now()}`,
    _id: `promo-${Date.now()}`,
    title,
    branch: branchId || branch || 'all',
    branchId: branchId || branch || 'all',
    discountPercent: discount,
    startDate,
    endDate,
    code: code ? code.toUpperCase() : `DESC${discount}`,
    active: true,
    createdByRole: req.user?.role || 'admin',
    createdAt: new Date().toISOString()
  };

  promotions.push(newPromo);
  res.json({ success: true, message: 'Promoción creada exitosamente', data: newPromo });
});

app.delete(['/api/admin/promotions/:id', '/api/host/promotions/:id', '/api/promotions/:id'], (req, res) => {
  const idx = promotions.findIndex(p => p.id === req.params.id || p._id === req.params.id);
  if (idx !== -1) promotions.splice(idx, 1);
  res.json({ success: true, message: 'Promoción eliminada exitosamente' });
});

app.get('/api/availability/peak-dates', (req, res) => {
  res.json({
    success: true,
    data: {
      peakDates: ['2026-12-24', '2026-12-25', '2026-12-31', '2026-01-01', '2026-03-29'],
      highSeasonMonths: [1, 6, 7, 12]
    }
  });
});

// 5. Bookings
app.post('/api/bookings', (req, res) => {
  cleanupExpiredBookings();
  const body = req.body || {};
  const today = new Date().toISOString().split('T')[0];

  if (!body.checkIn || body.checkIn < today) {
    return res.status(400).json({ success: false, message: 'La fecha de check-in debe ser igual o posterior al día de hoy.' });
  }
  if (!body.checkOut || body.checkOut <= body.checkIn) {
    return res.status(400).json({ success: false, message: 'La fecha de check-out debe ser posterior al check-in.' });
  }

  const suite = suites.find(s => s.id === body.suiteId || s.slug === body.suiteId) || suites[0];
  const maxCap = suite.maxGuests || 2;
  const guestsRequested = (Number(body.guests) || 1) + (Number(body.children) || 0);

  if (guestsRequested > maxCap) {
    return res.status(400).json({ success: false, message: `Esta habitación admite máximo ${maxCap} huéspedes en total.` });
  }

  if (suite.isAvailable === false || suite.available === false) {
    return res.status(400).json({ success: false, message: 'La suite seleccionada se encuentra temporalmente en mantenimiento.' });
  }

  // Minimum stay rule for peak season (Dec 24-31: minimum 2 nights)
  const isPeak = body.checkIn >= '2026-12-20' && body.checkIn <= '2026-12-31';
  const cIn = new Date(body.checkIn);
  const cOut = new Date(body.checkOut);
  const stayNights = Math.round((cOut - cIn) / 86400000);
  if (isPeak && stayNights < 2) {
    return res.status(400).json({ success: false, message: 'Para fechas festivas de fin de año se requiere una estancia mínima de 2 noches.' });
  }

  // Anti-overbooking check
  if (!isSuiteAvailableForDates(suite.id, body.checkIn, body.checkOut)) {
    return res.status(409).json({ success: false, message: 'La suite seleccionada ya no tiene disponibilidad para las fechas requeridas.' });
  }

  // Calculate detailed pricing
  const branch = branches.find(b => b.id === (suite.branchId || suite.branch || body.branchId)) || branches[0];
  const selectedExps = (body.experiences || []).map(expId => experiences.find(e => e.id === expId)).filter(Boolean);

  const pricing = calculateStayPricing({
    suite,
    checkIn: body.checkIn,
    checkOut: body.checkOut,
    guests: body.guests || 2,
    promoCode: body.promoCode || '',
    airportPickup: !!body.airportPickup,
    foodOrders: body.foodOrders || [],
    experiencesList: selectedExps
  });

  const bookingCode = `PDM-2026-${Math.floor(1000 + Math.random() * 9000)}`;
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString(); // 15-minute TTL

  const newBooking = {
    id: `bk-${Date.now()}`,
    _id: `bk-${Date.now()}`,
    bookingCode,
    userId: req.user?.id || 'guest',
    user: req.user ? sanitizeUser(req.user) : 'guest',
    suiteId: suite.id,
    suite: { ...suite, branch },
    branch,
    branchId: branch.id,
    checkIn: body.checkIn,
    checkOut: body.checkOut,
    nights: pricing.nights,
    guests: Number(body.guests) || 2,
    children: Number(body.children) || 0,
    totalPrice: pricing.totalPrice,
    paidAmount: 0,
    balanceDue: pricing.totalPrice,
    pricingBreakdown: pricing,
    status: 'pending',
    paymentStatus: 'pending',
    expiresAt,
    guestName: body.guestName || req.user?.name || 'Huésped',
    guestEmail: body.guestEmail || req.user?.email || 'huesped@palaciodelmar.co',
    guestPhone: body.guestPhone || req.user?.phone || '+57 300 000 0000',
    smartLockPin: null, // Generated upon full payment or pre-checkin
    preCheckInCompleted: false,
    checkedIn: false,
    checkedOut: false,
    isModifiable: true,
    isCancelable: true,
    experiences: selectedExps,
    foodOrders: body.foodOrders || [],
    airportPickup: !!body.airportPickup,
    notes: body.notes || '',
    statusHistory: [
      { status: 'pending', changedAt: new Date().toISOString(), changedBy: req.user?.name || 'Cliente', reason: 'Creación de reserva' }
    ],
    createdAt: new Date().toISOString()
  };

  bookings.unshift(newBooking);
  res.json({ success: true, message: 'Reserva creada exitosamente', data: newBooking });
});

app.get(['/api/bookings', '/api/bookings/my-bookings'], (req, res) => {
  cleanupExpiredBookings();
  const currentUserId = req.user?.id;
  const userBookings = currentUserId
    ? bookings.filter(b => b.userId === currentUserId || b.guestEmail === req.user?.email)
    : bookings;
  res.json({ success: true, data: userBookings });
});

app.get('/api/bookings/upcoming', (req, res) => {
  const upcoming = bookings.filter(b => b.status === 'confirmed' || b.status === 'pending');
  res.json({ success: true, data: upcoming });
});

app.get('/api/bookings/code/:bookingCode', (req, res) => {
  const booking = bookings.find(b => b.bookingCode === req.params.bookingCode);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  res.json({ success: true, data: booking });
});

app.get('/api/bookings/:id', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  res.json({ success: true, data: booking });
});

app.put(['/api/bookings/:id/modify', '/api/bookings/:id/modify-dates'], (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  if (booking.status === 'cancelled') return res.status(400).json({ success: false, message: 'No se puede modificar una reserva cancelada' });

  const newIn = req.body.checkIn || booking.checkIn;
  const newOut = req.body.checkOut || booking.checkOut;

  // Verify availability for new dates
  if (!isSuiteAvailableForDates(booking.suiteId, newIn, newOut, booking.id)) {
    return res.status(409).json({ success: false, message: 'No hay disponibilidad para el nuevo rango de fechas solicitado.' });
  }

  booking.checkIn = newIn;
  booking.checkOut = newOut;
  if (req.body.guests) booking.guests = req.body.guests;

  const suite = suites.find(s => s.id === booking.suiteId) || suites[0];
  const newPricing = calculateStayPricing({
    suite,
    checkIn: booking.checkIn,
    checkOut: booking.checkOut,
    guests: booking.guests,
    airportPickup: booking.airportPickup,
    foodOrders: booking.foodOrders,
    experiencesList: booking.experiences
  });

  booking.nights = newPricing.nights;
  booking.totalPrice = newPricing.totalPrice;
  booking.balanceDue = Math.max(0, booking.totalPrice - (booking.paidAmount || 0));

  res.json({ success: true, message: 'Reserva modificada exitosamente', data: booking });
});

// Cancellation with refund policies
app.all(['/api/bookings/:id/cancel', '/api/bookings/:id/cancellation'], (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  if (booking.status === 'cancelled') {
    return res.status(400).json({ success: false, message: 'La reserva ya se encuentra cancelada' });
  }

  const now = new Date();
  const cIn = new Date(booking.checkIn);
  const diffDays = (cIn - now) / (1000 * 60 * 60 * 24);

  const paid = booking.paidAmount || 0;
  let refunded = 0;
  let penalty = 0;

  if (paid > 0) {
    if (diffDays > 7) {
      // 100% refund
      refunded = paid;
      penalty = 0;
    } else {
      // 90% refund, 10% penalty
      penalty = Math.round(paid * 0.10);
      refunded = paid - penalty;
    }
  }

  booking.status = 'cancelled';
  booking.refundedAmount = refunded;
  booking.retentionPenalty = penalty;
  booking.balanceDue = 0;
  booking.statusHistory.push({
    status: 'cancelled',
    changedAt: new Date().toISOString(),
    changedBy: req.user?.name || 'Cliente',
    reason: req.body?.reason || `Cancelación de usuario. Reembolso: $${refunded.toLocaleString('es-CO')} COP (Penalidad: $${penalty.toLocaleString('es-CO')} COP)`
  });

  res.json({
    success: true,
    message: 'Reserva cancelada exitosamente',
    data: {
      ...booking,
      refundDetails: {
        paidAmount: paid,
        refundedAmount: refunded,
        retentionPenalty: penalty,
        policyApplied: diffDays > 7 ? 'Reembolso 100% (antelación mayor a 7 días)' : 'Retención 10% por gastos de gestión (antelación menor a 7 días)'
      }
    }
  });
});

app.post('/api/bookings/:id/pre-checkin', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  booking.preCheckInCompleted = true;
  if (!booking.smartLockPin) {
    booking.smartLockPin = String(Math.floor(1000 + Math.random() * 9000));
  }
  res.json({ success: true, message: 'Pre-check-in completado exitosamente. Llave digital activada.', data: booking });
});

app.get('/api/bookings/:id/smart-key', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  res.json({
    success: true,
    data: {
      bookingId: booking.id,
      smartLockPin: booking.smartLockPin || 'PENDIENTE',
      suite: booking.suite?.name || 'Suite',
      validFrom: `${booking.checkIn} 15:00`,
      validUntil: `${booking.checkOut} 11:00`,
      preCheckInCompleted: !!booking.preCheckInCompleted
    }
  });
});

// 6. Realistic Simulated Payment Gateway (PR #5 Parity)
app.get('/api/payments/config', (req, res) => {
  res.json({
    success: true,
    data: {
      currency: 'COP',
      gateway: 'simulated_wompi_stripe',
      testMode: true,
      allowedMethods: ['CARD', 'PSE', 'NEQUI', 'DAVIPLATA']
    }
  });
});

app.post('/api/payments/checkout', (req, res) => {
  const { bookingId } = req.body || {};
  const booking = bookings.find(b => b.id === bookingId || b._id === bookingId);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });

  if (booking.status === 'cancelled') {
    return res.status(400).json({ success: false, message: 'No se puede pagar una reserva cancelada o vencida' });
  }

  const now = new Date();
  if (booking.status === 'pending' && (!booking.paidAmount || booking.paidAmount === 0) && booking.expiresAt && new Date(booking.expiresAt) < now) {
    booking.status = 'expired';
    return res.status(400).json({ success: false, message: 'El tiempo para pagar esta reserva ha expirado' });
  }

  const intentId = `pi_${Date.now()}_${Math.random().toString(36).substring(7)}`;
  res.json({
    success: true,
    data: {
      intentId,
      clientSecret: `sec_${Date.now()}`,
      amount: booking.balanceDue,
      currency: 'COP',
      expiresInMinutes: 15,
      bookingId: booking.id
    }
  });
});

app.post(['/api/payments/process', '/api/payments/confirm'], (req, res) => {
  cleanupExpiredBookings();
  const idempotencyKey = req.headers['idempotency-key'];
  if (idempotencyKey && idempotentPayments.has(idempotencyKey)) {
    return res.json(idempotentPayments.get(idempotencyKey));
  }

  const { bookingId, amount, method, cardNumber, cardDetails, pseBank, phone } = req.body || {};
  const booking = bookings.find(b => b.id === bookingId || b._id === bookingId);

  if (!booking) {
    return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  }

  // 1. Booking State Guardrails
  if (booking.status === 'cancelled') {
    return res.status(400).json({ success: false, message: 'No se puede pagar una reserva cancelada o vencida' });
  }

  const now = new Date();
  if (booking.status === 'pending' && (!booking.paidAmount || booking.paidAmount === 0) && booking.expiresAt && new Date(booking.expiresAt) < now) {
    booking.status = 'expired';
    return res.status(400).json({ success: false, message: 'El tiempo para pagar esta reserva ha expirado' });
  }

  // 2. Overpayment Protection (PR #5 Parity)
  if (booking.balanceDue === 0 || booking.paymentStatus === 'paid') {
    return res.status(400).json({ success: false, message: 'La reserva ya ha sido pagada en su totalidad.' });
  }

  const payAmount = Number(amount) || booking.balanceDue;
  if (payAmount > booking.balanceDue) {
    return res.status(400).json({
      success: false,
      message: `El monto ingresado ($${payAmount.toLocaleString('es-CO')}) excede el saldo pendiente ($${booking.balanceDue.toLocaleString('es-CO')}).`
    });
  }

  // 3. Payment Method Validation & Test Card Simulation
  const pMethod = (method || 'CREDIT_CARD').toUpperCase();
  let authCode = `AUTH-${Math.floor(100000 + Math.random() * 900000)}`;
  let cusCode = null;

  if (pMethod.includes('CARD')) {
    const rawDigits = String(cardNumber || cardDetails?.cardNumber || '4242424242424242').replace(/\D/g, '');

    // Bank Test Cards
    if (rawDigits.endsWith('0002')) {
      return res.status(402).json({
        success: false,
        code: 'card_declined',
        message: 'Transacción declinada: Fondos insuficientes en la tarjeta de crédito.'
      });
    }
    if (rawDigits.endsWith('0019')) {
      return res.status(402).json({
        success: false,
        code: 'expired_card',
        message: 'Transacción declinada: La tarjeta bancaria ha expirado.'
      });
    }
    if (rawDigits.endsWith('0005')) {
      return res.status(402).json({
        success: false,
        code: 'fraud_risk',
        message: 'Transacción bloqueada por el sistema antifraude de la entidad bancaria.'
      });
    }

    if (!validateLuhn(rawDigits)) {
      return res.status(400).json({
        success: false,
        code: 'invalid_number',
        message: 'El número de tarjeta no supera la validación matemática de Luhn.'
      });
    }
  } else if (pMethod.includes('PSE')) {
    cusCode = `CUS-${Math.floor(100000 + Math.random() * 900000)}`;
  }

  // 4. Update Balances and States (Partial vs Paid)
  booking.paidAmount = (booking.paidAmount || 0) + payAmount;
  booking.balanceDue = Math.max(0, booking.totalPrice - booking.paidAmount);

  if (booking.balanceDue === 0) {
    booking.paymentStatus = 'paid';
    booking.status = 'confirmed';
    if (!booking.smartLockPin) {
      booking.smartLockPin = String(Math.floor(1000 + Math.random() * 9000));
    }
  } else {
    booking.paymentStatus = 'partial';
    booking.status = 'pending';
  }

  booking.statusHistory.push({
    status: booking.status,
    changedAt: new Date().toISOString(),
    changedBy: req.user?.name || 'Pasarela de Pago',
    reason: `Pago de $${payAmount.toLocaleString('es-CO')} COP recibido (${booking.paymentStatus === 'paid' ? 'Liquidación completa' : 'Abono parcial'}). Saldo restante: $${booking.balanceDue.toLocaleString('es-CO')} COP.`
  });

  const responsePayload = {
    success: true,
    message: booking.balanceDue === 0 ? 'Pago procesado exitosamente. Reserva confirmada.' : 'Abono parcial procesado exitosamente.',
    data: {
      paymentId: `pay_${Date.now()}`,
      status: 'succeeded',
      amount: payAmount,
      totalPrice: booking.totalPrice,
      paidAmount: booking.paidAmount,
      balanceDue: booking.balanceDue,
      paymentStatus: booking.paymentStatus,
      bookingStatus: booking.status,
      authCode,
      cusCode,
      booking
    }
  };

  if (idempotencyKey) {
    idempotentPayments.set(idempotencyKey, responsePayload);
  }

  res.json(responsePayload);
});

// 7. Host Management Endpoints
app.get('/api/host/bookings', (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = parseInt(req.query.limit) || 10;
  const search = (req.query.search || '').toLowerCase();

  let filtered = bookings;
  if (search) {
    filtered = filtered.filter(b =>
      (b.bookingCode && b.bookingCode.toLowerCase().includes(search)) ||
      (b.guestName && b.guestName.toLowerCase().includes(search)) ||
      (b.guestEmail && b.guestEmail.toLowerCase().includes(search))
    );
  }

  const total = filtered.length;
  const start = (page - 1) * limit;
  const data = filtered.slice(start, start + limit);

  res.json({
    success: true,
    data: {
      content: data,
      data,
      total,
      totalElements: total,
      totalPages: Math.ceil(total / limit) || 1,
      page,
      limit
    }
  });
});

app.get(['/api/host/metrics', '/api/host/stats'], (req, res) => {
  const today = new Date().toISOString().split('T')[0];
  const totalRevenue = bookings.reduce((sum, b) => sum + (b.paidAmount || 0), 0);
  const pendingCheckins = bookings.filter(b => b.status === 'confirmed' && b.checkIn === today && !b.checkedIn).length;
  const activeBookings = bookings.filter(b => b.status === 'confirmed' || b.status === 'pending').length;

  res.json({
    success: true,
    data: {
      totalSuites: suites.length,
      totalBookings: bookings.length,
      totalRevenue,
      pendingCheckins,
      activeBookings,
      averageOccupancy: 82
    }
  });
});

app.post('/api/host/bookings/:id/checkin', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  booking.checkedIn = true;
  booking.checkInTime = new Date().toISOString();
  booking.statusHistory.push({
    status: booking.status,
    changedAt: new Date().toISOString(),
    changedBy: req.user?.name || 'Anfitrión',
    reason: 'Check-in físico registrado en recepción'
  });
  res.json({ success: true, message: 'Check-in registrado exitosamente', data: booking });
});

app.post('/api/host/bookings/:id/checkout', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  booking.checkedOut = true;
  booking.status = 'completed';
  booking.checkOutTime = new Date().toISOString();
  booking.statusHistory.push({
    status: 'completed',
    changedAt: new Date().toISOString(),
    changedBy: req.user?.name || 'Anfitrión',
    reason: 'Check-out completado. Habitación liberada para aseo.'
  });
  res.json({ success: true, message: 'Check-out completado exitosamente', data: booking });
});

app.post('/api/host/bookings/:id/regenerate-pin', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  booking.smartLockPin = String(Math.floor(1000 + Math.random() * 9000));
  res.json({ success: true, message: 'PIN de cerradura electrónica regenerado', smartLockPin: booking.smartLockPin });
});

// 8. Admin Management Endpoints
app.get('/api/admin/bookings', (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = parseInt(req.query.limit) || 10;
  const status = req.query.status;
  const search = (req.query.search || '').toLowerCase();

  let filtered = bookings;
  if (status && status !== 'all') {
    filtered = filtered.filter(b => b.status === status);
  }
  if (search) {
    filtered = filtered.filter(b =>
      b.bookingCode.toLowerCase().includes(search) ||
      (b.guestName && b.guestName.toLowerCase().includes(search)) ||
      (b.guestEmail && b.guestEmail.toLowerCase().includes(search))
    );
  }

  const total = filtered.length;
  const start = (page - 1) * limit;
  const data = filtered.slice(start, start + limit);

  res.json({
    success: true,
    data: {
      content: data,
      data,
      total,
      totalElements: total,
      totalPages: Math.ceil(total / limit) || 1,
      page,
      limit
    }
  });
});

app.put('/api/admin/bookings/:id/status', (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  const oldStatus = booking.status;
  booking.status = req.body.status || 'confirmed';

  if (booking.status === 'confirmed' && !booking.smartLockPin) {
    booking.smartLockPin = String(Math.floor(1000 + Math.random() * 9000));
  }

  booking.statusHistory.push({
    status: booking.status,
    changedAt: new Date().toISOString(),
    changedBy: req.user?.name || 'Administrador',
    reason: req.body.reason || `Estado actualizado de ${oldStatus} a ${booking.status}`
  });

  res.json({ success: true, message: 'Estado actualizado exitosamente', data: booking });
});

app.post(['/api/admin/bookings/:id/approve', '/api/bookings/:id/approve'], (req, res) => {
  const booking = bookings.find(b => b.id === req.params.id || b._id === req.params.id);
  if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
  booking.status = 'confirmed';
  if (!booking.smartLockPin) {
    booking.smartLockPin = String(Math.floor(1000 + Math.random() * 9000));
  }
  booking.statusHistory.push({
    status: 'confirmed',
    changedAt: new Date().toISOString(),
    changedBy: req.user?.name || 'Administrador',
    reason: 'Aprobación manual de reserva por administración'
  });
  res.json({ success: true, message: 'Reserva aprobada exitosamente', data: booking });
});

app.put(['/api/admin/suites/:id/maintenance', '/api/suites/:id/status'], (req, res) => {
  const suite = suites.find(s => s.id === req.params.id || s._id === req.params.id || s.slug === req.params.id);
  if (!suite) return res.status(404).json({ success: false, message: 'Suite no encontrada' });
  const isAvailable = req.body?.isAvailable !== undefined ? !!req.body.isAvailable : !suite.isAvailable;
  suite.isAvailable = isAvailable;
  suite.available = isAvailable;
  res.json({ success: true, message: isAvailable ? 'Suite habilitada' : 'Suite bloqueada por mantenimiento', data: suite });
});

app.get(['/api/admin/stats', '/api/admin/metrics'], (req, res) => {
  const totalRevenue = bookings.reduce((sum, b) => sum + (b.paidAmount || 0) - (b.refundedAmount || 0), 0);
  const activeBookings = bookings.filter(b => b.status === 'confirmed' || b.status === 'pending').length;

  res.json({
    success: true,
    data: {
      totalBookings: bookings.length,
      activeBookings,
      totalRevenue: Math.max(0, totalRevenue),
      averageOccupancy: 84,
      totalUsers: users.length,
      totalSuites: suites.length,
      newClubMembers: 12
    }
  });
});

app.get('/api/admin/bookings/export-csv', (req, res) => {
  let csv = 'ID,Codigo,Huesped,Email,CheckIn,CheckOut,Total,Pagado,Saldo,Estado,PIN\n';
  bookings.forEach(b => {
    csv += `"${b.id}","${b.bookingCode}","${b.guestName}","${b.guestEmail}","${b.checkIn}","${b.checkOut}",${b.totalPrice},${b.paidAmount || 0},${b.balanceDue || 0},"${b.status}","${b.smartLockPin || ''}"\n`;
  });
  res.setHeader('Content-Type', 'text/csv');
  res.setHeader('Content-Disposition', 'attachment; filename="reservas-palacio-del-mar.csv"');
  res.send(csv);
});

// 9. Auth
app.post('/api/auth/register', (req, res) => {
  const { name, email, password, phone } = req.body || {};
  if (!email || !name) {
    return res.status(400).json({ success: false, message: 'Nombre y correo requeridos' });
  }
  let existing = findUserByEmail(email);
  if (existing) {
    return res.status(400).json({ success: false, message: 'El correo ya está registrado' });
  }
  const newUser = {
    id: `user-${Date.now()}`,
    _id: `user-${Date.now()}`,
    name,
    email,
    password: password || 'User123456!',
    phone: phone || '',
    role: 'user',
    status: 'active',
    emailVerified: true,
    createdAt: new Date().toISOString()
  };
  users.push(newUser);

  const safe = sanitizeUser(newUser);
  const token = jwt.sign({ id: newUser.id, role: newUser.role }, JWT_SECRET, { expiresIn: '7d' });
  res.cookie('jwt', token, { httpOnly: true, path: '/', maxAge: 7 * 86400000 });

  res.json({
    success: true,
    message: 'Usuario registrado exitosamente',
    token,
    user: safe,
    data: { token, user: safe }
  });
});

app.post('/api/auth/login', (req, res) => {
  const { email, password } = req.body || {};
  const user = findUserByEmail(email);
  if (!user || (password && user.password !== password)) {
    return res.status(401).json({ success: false, message: 'Credenciales inválidas' });
  }

  const safe = sanitizeUser(user);
  const token = jwt.sign({ id: user.id, role: user.role }, JWT_SECRET, { expiresIn: '7d' });
  res.cookie('jwt', token, { httpOnly: true, path: '/', maxAge: 7 * 86400000 });

  res.json({
    success: true,
    message: 'Inicio de sesión exitoso',
    token,
    user: safe,
    data: { token, user: safe }
  });
});

app.post('/api/auth/verify-email', (req, res) => {
  res.json({ success: true, message: 'Correo verificado exitosamente' });
});

app.get('/api/auth/me', (req, res) => {
  if (!req.user) {
    return res.status(401).json({ success: false, message: 'No autenticado' });
  }
  const safe = sanitizeUser(req.user);
  res.json({
    success: true,
    user: safe,
    data: { user: safe }
  });
});

app.post('/api/auth/logout', (req, res) => {
  res.clearCookie('jwt');
  res.json({ success: true, message: 'Sesión cerrada' });
});

app.put('/api/auth/profile', requireAuth, (req, res) => {
  const { name } = req.body || {};
  if (name) req.user.name = name;
  const safe = sanitizeUser(req.user);
  res.json({ success: true, message: 'Perfil actualizado exitosamente', data: safe, user: safe });
});

app.put('/api/auth/change-password', requireAuth, (req, res) => {
  const { newPassword } = req.body || {};
  if (newPassword) req.user.password = newPassword;
  res.json({ success: true, message: 'Contraseña actualizada exitosamente', data: null });
});

// 10. AI Chat Concierge
const CONCIERGE_PROMPT = `Eres Sofía, la concierge virtual de ultra lujo del hotel 'Palacio del Mar' en Cartagena de Indias.
Palacio del Mar cuenta con 4 sedes exclusivas:
1. Centro Histórico: Casona colonial amurallada, suites imperiales, romántico y caminable.
2. Getsemaní: Arte callejero, patio bohemio, música y gastronomía en la Calle del Arsenal.
3. Bocagrande: Frente al Mar Caribe, rascacielos contemporáneo, vistas panorámicas.
4. La Boquilla: Playa privada, manglares, tranquilidad ecológica y villas frente al mar.
Precios desde $680.000 COP hasta $2.780.000 COP la noche.
Responde con máxima elegancia caribeña, calidez, concisión (2 a 4 oraciones) y tono de alta hospitalidad.`;

app.post('/api/chat', async (req, res) => {
  const { message, history } = req.body || {};
  const clean = (message || '').trim();
  if (!clean) return res.status(400).json({ error: 'El mensaje no puede estar vacío' });

  const apiKey = process.env.GEMINI_API_KEY;
  let reply = '';
  let aiEnabled = false;

  if (apiKey) {
    try {
      const ai = new GoogleGenAI({ apiKey });
      const contents = [];
      if (Array.isArray(history)) {
        for (const h of history.slice(-6)) {
          contents.push({
            role: h.role === 'user' ? 'user' : 'model',
            parts: [{ text: h.content || '' }]
          });
        }
      }
      contents.push({ role: 'user', parts: [{ text: clean }] });

      const response = await ai.models.generateContent({
        model: 'gemini-1.5-flash',
        contents,
        config: { systemInstruction: CONCIERGE_PROMPT }
      });
      reply = response.text || '';
      aiEnabled = true;
    } catch (e) {
      console.warn('Gemini chat error, falling back:', e.message);
    }
  }

  if (!reply) {
    const lower = clean.toLowerCase();
    if (lower.includes('playa') || lower.includes('mar') || lower.includes('bocagrande') || lower.includes('boquilla')) {
      reply = 'Para disfrutar directamente de la brisa caribeña y la arena suave, le recomiendo nuestras sedes de Bocagrande frente al océano o el santuario de tranquilidad en La Boquilla con salida directa a la playa.';
    } else if (lower.includes('romantico') || lower.includes('pareja') || lower.includes('historico') || lower.includes('colonial')) {
      reply = 'Para una velada inolvidable en pareja, la Casona del Centro Histórico dentro de la muralla y Getsemaní ofrecen patios coloniales, tinas de hidromasaje y atardeceres mágicos.';
    } else if (lower.includes('precio') || lower.includes('costo') || lower.includes('tarifa')) {
      reply = 'Nuestras tarifas exclusivas inician desde $680.000 COP en bungalows caribeños hasta $2.780.000 COP en la Gran Suite Presidencial. Incluyen amenidades de lujo y servicio personalizado.';
    } else {
      reply = 'Es un absoluto placer asistirle en Palacio del Mar. ¿Desea explorar nuestras suites coloniales amuralladas, nuestras villas playeras, o reservar una experiencia gastronómica exclusiva?';
    }
  }

  res.json({
    reply,
    success: true,
    metadata: {
      language: 'es',
      responseTime: 110,
      aiEnabled
    }
  });
});

app.post('/api/chat/vibe-score', (req, res) => {
  const { vibe, suites: incomingSuites } = req.body || {};
  const scores = {};
  const v = (vibe || '').toLowerCase();
  (incomingSuites || suites).forEach(s => {
    let score = 75;
    const name = (s.name || '').toLowerCase();
    if (v.includes('romant') && (name.includes('colonial') || s.hasJacuzzi)) score += 20;
    if (v.includes('playa') && (s.view === 'ocean' || name.includes('beach') || name.includes('mar'))) score += 20;
    if (v.includes('lujo') && (name.includes('presidencial') || name.includes('penthouse') || name.includes('imperial'))) score += 20;
    scores[s.id || s._id] = Math.min(100, score);
  });
  res.json({ success: true, data: scores });
});

app.get('/api/chat/status', (req, res) => {
  res.json({
    success: true,
    data: {
      status: 'active',
      aiEnabled: !!process.env.GEMINI_API_KEY
    }
  });
});

// --- STATIC ASSETS & SPA SERVING ---
const angularDistPath = path.join(__dirname, 'frontend-angular', 'dist', 'palacio-del-mar-web', 'browser');

if (!fs.existsSync(path.join(angularDistPath, 'index.html'))) {
  console.log('Angular production bundle not found. Building bundle...');
  try {
    execSync('npm --prefix frontend-angular run build', { stdio: 'inherit' });
  } catch (err) {
    console.error('Failed to build Angular frontend:', err);
  }
}

app.use(express.static(angularDistPath));

app.get('*', (req, res) => {
  const indexFile = path.join(angularDistPath, 'index.html');
  if (fs.existsSync(indexFile)) {
    res.sendFile(indexFile);
  } else {
    res.status(503).send('Application is compiling, please refresh momentarily.');
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Palacio del Mar server running at http://0.0.0.0:${PORT}`);
});
