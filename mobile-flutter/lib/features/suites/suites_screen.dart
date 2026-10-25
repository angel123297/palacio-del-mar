import 'package:flutter/material.dart';
import 'filtros_widget.dart';

class SuitesScreen extends StatefulWidget {
  const SuitesScreen({super.key});

  @override
  State<SuitesScreen> createState() => _SuitesScreenState();
}

class _SuitesScreenState extends State<SuitesScreen> {
  String _selectedZone = 'Todas';

  final List<Map<String, dynamic>> _suites = [
    {
      'title': 'Superior Patio Colonial',
      'zone': 'Centro Histórico',
      'category': 'HABITACIÓN',
      'price': '\$ 860.000',
      'oldPrice': '\$ 1.050.000',
      'image': 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800',
      'recommended': true,
      'amenities': ['JARDÍN PRIVADO', 'AIRE ACONDICIONADO', 'MINIBAR', '42 M²', 'HASTA 2 HUÉSPEDES'],
    },
    {
      'title': 'Suite Colonial Imperial',
      'zone': 'Centro Histórico',
      'category': 'SUITE DELUXE',
      'price': '\$ 1.310.000',
      'oldPrice': '\$ 1.650.000',
      'image': 'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800',
      'recommended': true,
      'amenities': ['PATIO COLONIAL', 'TINA HIDROMASAJE', 'BALCÓN', '65 M²', 'HASTA 2 HUÉSPEDES'],
    },
    {
      'title': 'Suite Vista Murallas',
      'zone': 'Centro Histórico',
      'category': 'SUITE PREMIUM',
      'price': '\$ 1.720.000',
      'oldPrice': '\$ 2.150.000',
      'image': 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800',
      'recommended': true,
      'amenities': ['VISTA A MURALLAS', 'JACUZZI', 'BALCÓN PRIVADO', '85 M²', 'HASTA 2 HUÉSPEDES'],
    },
    {
      'title': 'Gran Suite Presidencial',
      'zone': 'Centro Histórico',
      'category': 'SUITE PRESIDENCIAL',
      'price': '\$ 2.780.000',
      'oldPrice': '\$ 3.440.000',
      'image': 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
      'recommended': true,
      'amenities': ['TERRAZA PRIVADA', 'JACUZZI', 'MAYORDOMO 24/7', '180 M²', 'HASTA 4 HUÉSPEDES'],
    },
    {
      'title': 'Habitación Bohemia',
      'zone': 'Getsemaní',
      'category': 'HABITACIÓN',
      'price': '\$ 730.000',
      'oldPrice': '\$ 890.000',
      'image': 'https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?w=800',
      'recommended': true,
      'amenities': ['PATIO BOHEMIO', 'AIRE ACONDICIONADO', 'WIFI ULTRA RÁPIDO', '40 M²', 'HASTA 2 HUÉSPEDES'],
    },
    {
      'title': 'Suite Arte & Patio',
      'zone': 'Getsemaní',
      'category': 'SUITE DELUXE',
      'price': '\$ 1.150.000',
      'oldPrice': '\$ 1.390.000',
      'image': 'https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800',
      'recommended': true,
      'amenities': ['COLECCIÓN DE ARTE', 'PATIO PRIVADO', 'MINIBAR GOURMET', '58 M²', 'HASTA 2 HUÉSPEDES'],
    },
    {
      'title': 'Executive Ocean View',
      'zone': 'Bocagrande',
      'category': 'HABITACIÓN',
      'price': '\$ 920.000',
      'oldPrice': '\$ 1.120.000',
      'image': 'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?w=800',
      'recommended': false,
      'amenities': ['VISTA AL MAR', 'ESCRITORIO DE TRABAJO', 'SMART TV 65"', '45 M²', 'HASTA 2 HUÉSPEDES'],
    },
    {
      'title': 'Penthouse Ocean Front',
      'zone': 'Bocagrande',
      'category': 'SUITE PRESIDENCIAL',
      'price': '\$ 2.450.000',
      'oldPrice': '\$ 3.100.000',
      'image': 'https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=800',
      'recommended': true,
      'amenities': ['PISCINA PRIVADA', 'MAYORDOMO PRIVADO', 'CHEF BAJO SOLICITUD', '160 M²', 'HASTA 4 HUÉSPEDES'],
    },
    {
      'title': 'Bungalow Caribe',
      'zone': 'La Boquilla',
      'category': 'HABITACIÓN',
      'price': '\$ 680.000',
      'oldPrice': '\$ 820.000',
      'image': 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800',
      'recommended': false,
      'amenities': ['SALIDA DIRECTA A PLAYA', 'HAMACAS', 'CLIMATIZACIÓN', '48 M²', 'HASTA 2 HUÉSPEDES'],
    },
  ];

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const darkBg = Color(0xFF0B0C10);
    const cardBg = Color(0xFF141414);

    final filteredSuites = _selectedZone == 'Todas'
        ? _suites
        : _suites.where((s) => s['zone'] == _selectedZone).toList();

    return Scaffold(
      backgroundColor: darkBg,
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Container(
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF181818),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: goldColor.withOpacity(0.3)),
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(child: _searchTile('LLEGADA', '18 Oct 2026', goldColor)),
                      Container(height: 30, width: 1, color: Colors.white12),
                      Expanded(child: _searchTile('SALIDA', '20 Oct 2026', goldColor)),
                    ],
                  ),
                  const Divider(color: Colors.white12, height: 20),
                  Row(
                    children: [
                      Expanded(child: _searchTile('ADULTOS', '2 adultos', goldColor)),
                      Container(height: 30, width: 1, color: Colors.white12),
                      Expanded(child: _searchTile('VIBE', 'Cualquiera', goldColor)),
                    ],
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: goldColor,
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                      ),
                      onPressed: () => showModalBottomSheet(context: context, builder: (ctx) => FiltrosWidget(onFiltrosAplicados: (p, h, pf) { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Filtros aplicados'))); })),
                      child: const Text('BUSCAR SUITES', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                    ),
                  )
                ],
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('NUESTRAS UBICACIONES EXCLUSIVAS', style: TextStyle(color: goldColor, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  const Text('Elige tu Palacio en Cartagena', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: ['Todas', 'Centro Histórico', 'Getsemaní', 'Bocagrande', 'La Boquilla'].map((zone) {
                        final isSelected = _selectedZone == zone;
                        return Padding(
                          padding: const EdgeInsets.only(right: 8.0),
                          child: ChoiceChip(
                            label: Text(zone),
                            selected: isSelected,
                            onSelected: (_) => setState(() => _selectedZone = zone),
                            selectedColor: goldColor,
                            backgroundColor: const Color(0xFF1E1E1E),
                            labelStyle: TextStyle(
                              color: isSelected ? Colors.black : Colors.white70,
                              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                              fontSize: 11,
                            ),
                            side: BorderSide(color: isSelected ? goldColor : Colors.white12),
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  final suite = filteredSuites[index];
                  final List<String> amenities = List<String>.from(suite['amenities']);

                  return Container(
                    margin: const EdgeInsets.only(bottom: 24),
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Stack(
                          children: [
                            ClipRRect(
                              borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
                              child: Image.network(
                                suite['image'],
                                height: 210,
                                width: double.infinity,
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) => Container(
                                  height: 210,
                                  color: Colors.white10,
                                  child: const Center(child: Icon(Icons.hotel, color: goldColor, size: 48)),
                                ),
                              ),
                            ),
                            if (suite['recommended'] == true)
                              Positioned(
                                top: 12,
                                right: 12,
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFF2E7D32),
                                    borderRadius: BorderRadius.circular(4),
                                  ),
                                  child: const Text('RECOMENDADA', style: TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.bold, letterSpacing: 1)),
                                ),
                              ),
                          ],
                        ),
                        Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${suite['category']} · ${suite['zone']}'.toUpperCase(),
                                style: const TextStyle(color: goldColor, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                suite['title'],
                                style: const TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(height: 12),
                              Wrap(
                                spacing: 6,
                                runSpacing: 6,
                                children: amenities.map((amenity) {
                                  return Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: Colors.white.withOpacity(0.05),
                                      borderRadius: BorderRadius.circular(2),
                                      border: Border.all(color: Colors.white12),
                                    ),
                                    child: Text(amenity, style: const TextStyle(color: Colors.white60, fontSize: 9, letterSpacing: 0.5)),
                                  );
                                }).toList(),
                              ),
                              const SizedBox(height: 16),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        suite['oldPrice'],
                                        style: const TextStyle(color: Colors.white38, fontSize: 11, decoration: TextDecoration.lineThrough),
                                      ),
                                      Row(
                                        children: [
                                          Text(
                                            suite['price'],
                                            style: const TextStyle(color: goldColor, fontSize: 18, fontWeight: FontWeight.bold),
                                          ),
                                          const Text(' / noche', style: TextStyle(color: Colors.white54, fontSize: 12)),
                                        ],
                                      ),
                                    ],
                                  ),
                                  ElevatedButton(
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: Colors.transparent,
                                      foregroundColor: goldColor,
                                      elevation: 0,
                                      side: const BorderSide(color: goldColor, width: 1),
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                                    ),
                                    onPressed: () {
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        SnackBar(content: Text('Reserva seleccionada: ${suite['title']}'), backgroundColor: goldColor),
                                      );
                                    },
                                    child: const Text('RESERVAR', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  );
                },
                childCount: filteredSuites.length,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _searchTile(String label, String value, Color goldColor) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: Colors.white38, fontSize: 9, letterSpacing: 1, fontWeight: FontWeight.bold)),
          const SizedBox(height: 2),
          Text(value, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
