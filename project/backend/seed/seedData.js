import dotenv from 'dotenv';
import mongoose from 'mongoose';

// Cargar variables de entorno
dotenv.config();

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/palacio_del_mar';

import Suite from '../models/Suite.js';
import Experience from '../models/Experience.js';

export const suites = [
  { 
    name: 'Superior Patio',
    slug: 'superior-patio',
    type: 'Habitación',
    description: 'Acogedora habitación con jardín privado, ideal para una estancia relajante en el corazón de Cartagena.',
    shortDescription: 'Habitación acogedora con jardín privado',
    basePrice: 860000,
    originalPrice: 1050000,
    size: 42,
    mainImage: 'https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Jardín privado', 'Aire acondicionado', 'Minibar', 'WiFi gratuito', 'TV LED'],
    maxGuests: 2,
    bathrooms: 1,
    order: 1,
    available: true,
    featured: false
  },
  { 
    name: 'Suite Colonial',
    slug: 'suite-colonial',
    type: 'Suite Deluxe',
    description: 'Suite histórica con patio colonial y tina hidromasaje, combinando el encanto del pasado con comodidades modernas.',
    shortDescription: 'Suite histórica con patio colonial',
    basePrice: 1310000,
    originalPrice: 1650000,
    size: 65,
    mainImage: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Patio colonial', 'Tina hidromasaje', 'Balcón', 'Aire acondicionado', 'Minibar', 'WiFi gratuito'],
    maxGuests: 2,
    bathrooms: 1,
    order: 2,
    available: true,
    featured: true
  },
  { 
    name: 'Suite Bahía',
    slug: 'suite-bahia',
    type: 'Suite Premium',
    description: 'Espaciosa suite con vista al mar y jacuzzi privado. Disfruta del atardecer caribeño desde tu balcón.',
    shortDescription: 'Suite espaciosa con vista al mar y jacuzzi',
    basePrice: 1720000,
    originalPrice: 2150000,
    size: 85,
    mainImage: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Vista al mar', 'Jacuzzi', 'Balcón privado', 'Aire acondicionado', 'Minibar', 'WiFi gratuito', 'TV LED'],
    maxGuests: 2,
    bathrooms: 2,
    order: 3,
    available: true,
    featured: true
  },
  { 
    name: 'Suite Presidencial',
    slug: 'suite-presidencial',
    type: 'Suite Presidencial',
    description: 'La máxima expresión de lujo. Terraza privada con jacuzzi, mayordomo personal y vistas espectaculares.',
    shortDescription: 'Lujo máximo con terraza privada y mayordomo',
    basePrice: 2780000,
    originalPrice: 3440000,
    size: 180,
    mainImage: 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Terraza privada', 'Jacuzzi', 'Mayordomo 24/7', 'Sala de estar', 'Cama king', 'Aire acondicionado', 'Minibar', 'WiFi gratuito'],
    maxGuests: 4,
    bathrooms: 2,
    hasJacuzzi: true,
    hasBalcony: true,
    hasTerrace: true,
    view: 'ocean',
    order: 4,
    available: true,
    featured: true
  },
  { 
    name: 'Penthouse Muralla',
    slug: 'penthouse-muralla',
    type: 'Suite Exclusiva',
    description: 'El ático más exclusivo con vista 360° de la ciudad. Piscina privada y terraza doble para eventos exclusivos.',
    shortDescription: 'Ático exclusivo con piscina privada y vista 360°',
    basePrice: 3640000,
    originalPrice: 4280000,
    size: 220,
    mainImage: 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop&q=80'],
    amenities: ['Vista 360°', 'Piscina privada', 'Terraza doble', 'Cocina completa', 'Mayordomo', 'Sala de estar', 'Comedor', 'Aire acondicionado'],
    maxGuests: 6,
    bathrooms: 3,
    hasJacuzzi: true,
    hasBalcony: true,
    hasTerrace: true,
    view: 'ocean',
    order: 5,
    available: true,
    featured: true
  }
];

export const experiences = [
  { 
    name: 'Islas del Rosario',
    slug: 'islas-del-rosario',
    shortDescription: 'Excursión a las Islas del Rosario con snorkel y almuerzo',
    description: 'Navegación privada a las cristalinas aguas del Parque Natural Nacional Rosario. Snorkel en arrecifes de coral, avistamiento de peces tropicales y almuerzo típico caribeño.',
    price: 348000,
    durationHours: 8,
    category: 'aventura',
    location: { 
      name: 'Islas del Rosario, Cartagena',
      address: 'Parque Nacional Natural Corales del Rosario',
      meetingPoint: 'Muelle Turístico de La Bodeguita'
    },
    mainImage: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80'],
    icon: '⛵',
    maxCapacity: 12,
    included: ['Transporte ida y vuelta', 'Snorkel', 'Almuerzo', 'Bebidas', 'Guía'],
    requirements: ['Traje de baño', 'Protector solar', 'Toalla'],
    available: true,
    featured: true
  },
  { 
    name: 'Atardecer en la Muralla',
    slug: 'atardecer-muralla',
    shortDescription: 'Atardecer exclusivo en la muralla con coctel y ron artesanal',
    description: 'Cóctel al atardecer sobre la muralla colonial con vistas al Caribe y degustación de ron artesanal colombiano.',
    price: 184000,
    durationHours: 2.5,
    category: 'cultural',
    location: { 
      name: 'Muralla Colonial, Cartagena',
      address: 'Murallas de Cartagena, Centro Histórico',
      meetingPoint: 'Puerta del Reloj'
    },
    mainImage: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80'],
    icon: '🏛️',
    maxCapacity: 20,
    included: ['Coctel de bienvenida', 'Degustación de ron', 'Tabla de quesos', 'Frutas'],
    available: true,
    featured: true
  },
  { 
    name: 'Ruta del Sabor',
    slug: 'ruta-del-sabor',
    shortDescription: 'Tour gastronómico por mercados locales con chef',
    description: 'Tour gastronómico por mercados locales con nuestro chef ejecutivo. Cocina en vivo y degustación de platos típicos.',
    price: 266000,
    durationHours: 4,
    category: 'gastronomía',
    location: { 
      name: 'Centro Histórico, Cartagena',
      address: 'Mercado de Bazurto, Cartagena',
      meetingPoint: 'Lobby del hotel'
    },
    mainImage: 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80'],
    icon: '🌿',
    maxCapacity: 10,
    included: ['Tour guiado', 'Clase de cocina', 'Degustación', 'Recetario'],
    requirements: ['Ropa cómoda', 'Zapatos cerrados'],
    available: true,
    featured: false
  },
  { 
    name: 'Noche de Palenque',
    slug: 'noche-palenque',
    shortDescription: 'Noche cultural con música y baile de Palenque',
    description: 'Noche cultural con músicos de Palenque, baile de mapalé y cena en terraza bajo el cielo caribeño.',
    price: 389000,
    durationHours: 3,
    category: 'cultural',
    location: { 
      name: 'Palenque de San Basilio, Cartagena',
      address: 'Terraza Palacio del Mar, Centro Histórico',
      meetingPoint: 'Terraza principal del hotel'
    },
    mainImage: 'https://images.unsplash.com/photo-1534367507873-de2f85e8dbdf?w=800&auto=format&fit=crop&q=80',
    images: ['https://images.unsplash.com/photo-1534367507873-de2f85e8dbdf?w=800&auto=format&fit=crop&q=80'],
    icon: '🎶',
    maxCapacity: 30,
    included: ['Espectáculo', 'Cena típica', 'Bebidas', 'Clase de baile'],
    available: true,
    featured: true
  }
];

/**
 * Inserta los datos de ejemplo usando la conexión de Mongoose YA ABIERTA
 * (por el llamador). No conecta ni desconecta, y no llama a process.exit:
 * así puede reutilizarse tanto desde este script de línea de comandos como
 * desde el arranque automático del servidor (ver ../bootstrap.js).
 */
export const insertSeedData = async ({ clear = true } = {}) => {
  if (clear) {
    await Suite.deleteMany();
    await Experience.deleteMany();
  }
  for (const suite of suites) {
    await Suite.create(suite);
  }
  for (const exp of experiences) {
    await Experience.create(exp);
  }
  return { suites: suites.length, experiences: experiences.length };
};

/**
 * Punto de entrada cuando se ejecuta como script:
 *   npm run seed
 *   docker compose --profile seed run --rm seed
 * Conecta, siembra y sale del proceso. Útil para volver a cargar el
 * catálogo de ejemplo a mano; el arranque normal del servidor ya siembra
 * automáticamente si la base de datos está vacía (ver bootstrap.js).
 */
const seedDatabase = async () => {
  try {
    console.log('📡 Conectando a MongoDB...');
    console.log('🔗 URI:', MONGODB_URI);
    
    await mongoose.connect(MONGODB_URI);
    console.log('✅ Conectado a MongoDB');
    
    console.log('🧹 Limpiando colecciones existentes...');
    const result = await insertSeedData({ clear: true });
    
    console.log('\n✨ ¡Seed completado exitosamente! ✨');
    console.log(`📊 Resumen:`);
    console.log(`   - Suites: ${result.suites}`);
    console.log(`   - Experiencias: ${result.experiences}`);
    
    process.exit(0);
  } catch (error) {
    console.error('❌ Error en seed:', error.message);
    if (error.message.includes('ECONNREFUSED')) {
      console.log('\n💡 MongoDB no está corriendo. Ejecuta:');
      console.log('   net start MongoDB');
    }
    process.exit(1);
  }
};

// Solo se ejecuta como script (no al importar suites/experiences/insertSeedData
// desde bootstrap.js)
if (import.meta.url === `file://${process.argv[1]}`) {
  seedDatabase();
}