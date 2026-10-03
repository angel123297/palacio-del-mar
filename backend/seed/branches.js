import Branch from '../models/Branch.js';

// Sucursales de ejemplo (datos ficticios; las coordenadas son aproximadas,
// pensadas para que el mapa quede en la zona correcta, no para navegar).
export const branches = [
  {
    name: 'Palacio del Mar · Centro Histórico',
    slug: 'centro-historico',
    zone: 'Centro Histórico',
    tagline: 'Dentro de la ciudad amurallada, a pasos de todo',
    description:
      'Casona colonial restaurada dentro de las murallas. Calles empedradas, plazas con vida y los principales monumentos de Cartagena a pocos minutos caminando.',
    address: 'Calle de la Factoría, Centro Histórico, Cartagena',
    location: { lat: 10.4236, lng: -75.5515 },
    vibe: ['histórico', 'romántico', 'caminable'],
    highlights: [
      { name: 'Torre del Reloj', type: 'historia', walkMinutes: 6, location: { lat: 10.4247, lng: -75.5513 } },
      { name: 'Plaza de Santo Domingo', type: 'gastronomia', walkMinutes: 3, location: { lat: 10.4237, lng: -75.5517 } },
      { name: 'Palacio de la Inquisición', type: 'cultura', walkMinutes: 5, location: { lat: 10.4238, lng: -75.5535 } },
      { name: 'Murallas al atardecer', type: 'historia', walkMinutes: 8, location: { lat: 10.4264, lng: -75.5509 } },
      { name: 'Castillo de San Felipe', type: 'historia', walkMinutes: 25, location: { lat: 10.4228, lng: -75.539 } }
    ],
    order: 1
  },
  {
    name: 'Palacio del Mar · Getsemaní',
    slug: 'getsemani',
    zone: 'Getsemaní',
    tagline: 'Arte callejero, música y la mejor vida nocturna',
    description:
      'Casa de barrio con patio, en el sector más colorido y vivo de la ciudad. Ideal para quien quiere cultura local, murales, bares y buena comida a toda hora.',
    address: 'Calle del Arsenal, Getsemaní, Cartagena',
    location: { lat: 10.4225, lng: -75.5462 },
    vibe: ['artístico', 'nocturno', 'bohemio'],
    highlights: [
      { name: 'Plaza de la Trinidad', type: 'vida-nocturna', walkMinutes: 4, location: { lat: 10.4211, lng: -75.547 } },
      { name: 'Murales y arte callejero', type: 'cultura', walkMinutes: 2 },
      { name: 'Camellón de los Mártires', type: 'historia', walkMinutes: 7, location: { lat: 10.4249, lng: -75.5468 } },
      { name: 'Ciudad amurallada', type: 'historia', walkMinutes: 10, location: { lat: 10.4247, lng: -75.5513 } }
    ],
    order: 2
  },
  {
    name: 'Palacio del Mar · Bocagrande',
    slug: 'bocagrande',
    zone: 'Bocagrande',
    tagline: 'Frente a la playa, con restaurantes y compras',
    description:
      'Edificio frente al mar en la zona hotelera moderna: playa a la puerta, restaurantes, centros comerciales y vistas abiertas a la bahía.',
    address: 'Avenida San Martín, Bocagrande, Cartagena',
    location: { lat: 10.401, lng: -75.5575 },
    vibe: ['playero', 'familiar', 'moderno'],
    highlights: [
      { name: 'Playa de Bocagrande', type: 'playa', walkMinutes: 2, location: { lat: 10.4005, lng: -75.5565 } },
      { name: 'Avenida San Martín', type: 'gastronomia', walkMinutes: 1, location: { lat: 10.4015, lng: -75.5585 } },
      { name: 'Castillogrande', type: 'compras', walkMinutes: 15, location: { lat: 10.3925, lng: -75.5522 } },
      { name: 'Centros comerciales', type: 'compras', walkMinutes: 10 }
    ],
    order: 3
  },
  {
    name: 'Palacio del Mar · La Boquilla',
    slug: 'la-boquilla',
    zone: 'La Boquilla',
    tagline: 'Mar, manglares y tranquilidad al norte',
    description:
      'Refugio frente a la playa y la ciénaga, lejos del bullicio. Perfecto para descansar, comer pescado fresco y recorrer los manglares en canoa.',
    address: 'Vía al Mar, La Boquilla, Cartagena',
    location: { lat: 10.47, lng: -75.512 },
    vibe: ['tranquilo', 'naturaleza', 'playero'],
    highlights: [
      { name: 'Playa de La Boquilla', type: 'playa', walkMinutes: 3, location: { lat: 10.4695, lng: -75.5105 } },
      { name: 'Manglares y paseo en canoa', type: 'naturaleza', walkMinutes: 8, location: { lat: 10.4735, lng: -75.5135 } },
      { name: 'Restaurantes de pescado frito', type: 'gastronomia', walkMinutes: 4 }
    ],
    order: 4
  }
];

/**
 * Crea las sucursales que falten (por slug). No pisa las que ya existen, así
 * lo que el anfitrión edite más adelante se conserva entre reinicios.
 * Devuelve un Map slug → documento.
 */
export const ensureBranches = async () => {
  const bySlug = new Map();
  for (const data of branches) {
    const doc = await Branch.findOneAndUpdate(
      { slug: data.slug },
      { $setOnInsert: data },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    bySlug.set(data.slug, doc);
  }
  return bySlug;
};
