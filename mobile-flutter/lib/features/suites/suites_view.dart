import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';
import '../../core/services/api_service.dart';

class SuitesCatalogView extends StatefulWidget {
  const SuitesCatalogView({super.key});

  @override
  State<SuitesCatalogView> createState() => _SuitesCatalogViewState();
}

class _SuitesCatalogViewState extends State<SuitesCatalogView> {
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
      'amenities': ['JARDÍN PRIVADO', 'AIRE ACONDICIONADO', 'MINIBAR', '42 M²'],
    },
    {
      'title': 'Suite Colonial Imperial',
      'zone': 'Centro Histórico',
      'category': 'SUITE DELUXE',
      'price': '\$ 1.310.000',
      'oldPrice': '\$ 1.650.000',
      'image': 'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800',
      'recommended': true,
      'amenities': ['PATIO COLONIAL', 'TINA HIDROMASAJE', 'BALCÓN', '65 M²'],
    },
    {
      'title': 'Suite Vista Murallas',
      'zone': 'Centro Histórico',
      'category': 'SUITE PREMIUM',
      'price': '\$ 1.720.000',
      'oldPrice': '\$ 2.150.000',
      'image': 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800',
      'recommended': true,
      'amenities': ['VISTA A MURALLAS', 'JACUZZI', 'BALCÓN PRIVADO', '85 M²'],
    },
    {
      'title': 'Gran Suite Presidencial',
      'zone': 'Centro Histórico',
      'category': 'SUITE PRESIDENCIAL',
      'price': '\$ 2.780.000',
      'oldPrice': '\$ 3.440.000',
      'image': 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
      'recommended': true,
      'amenities': ['TERRAZA PRIVADA', 'JACUZZI', 'MAYORDOMO 24/7', '180 M²'],
    },
  ];

  void _openBookingModal(Map<String, dynamic> suite) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg,
      builder: (context) {
        return Padding(
          padding: EdgeInsets.only(
            top: 24,
            left: 24,
            right: 24,
            bottom: MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(suite['title'], style: const TextStyle(fontFamily: 'serif', fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
              Text('${suite['category']} · ${suite['zone']}', style: const TextStyle(color: AppTheme.gold, fontSize: 12)),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Tarifa por noche:', style: TextStyle(color: Colors.white70)),
                  Text(suite['price'], style: const TextStyle(color: AppTheme.gold, fontWeight: FontWeight.bold, fontSize: 18)),
                ],
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.gold,
                    foregroundColor: Colors.black,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  onPressed: () async {
                    Navigator.pop(context);
                    await ApiService.createBooking({
                      'roomName': suite['title'],
                      'zone': suite['zone'],
                      'price': suite['price'],
                      'status': 'CONFIRMADA',
                    });
                    if (!mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('¡Reserva para "${suite['title']}" registrada en la BD!'),
                        backgroundColor: AppTheme.gold,
                      ),
                    );
                  },
                  child: const Text('CONFIRMAR Y GUARDAR EN BD', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1)),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _selectedZone == 'Todas'
        ? _suites
        : _suites.where((s) => s['zone'] == _selectedZone).toList();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('NUESTRAS SUITES', style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          const Text('Elige tu Palacio en Cartagena', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
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
                    selectedColor: AppTheme.gold,
                    backgroundColor: const Color(0xFF1E1E1E),
                    labelStyle: TextStyle(
                      color: isSelected ? Colors.black : Colors.white70,
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      fontSize: 11,
                    ),
                    side: BorderSide(color: isSelected ? AppTheme.gold : Colors.white12),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 16),
          ...filtered.map((suite) {
            final List<String> amenities = List<String>.from(suite['amenities']);
            return Container(
              margin: const EdgeInsets.only(bottom: 20),
              decoration: BoxDecoration(color: AppTheme.cardBg, borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.white10)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ClipRRect(
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
                    child: Image.network(
                      suite['image'],
                      height: 190,
                      width: double.infinity,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(height: 190, color: Colors.white10, child: const Center(child: Icon(Icons.hotel, color: AppTheme.gold, size: 40))),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('${suite['category']} · ${suite['zone']}'.toUpperCase(), style: const TextStyle(color: AppTheme.gold, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 1)),
                        const SizedBox(height: 4),
                        Text(suite['title'], style: const TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 6,
                          runSpacing: 6,
                          children: amenities.map((a) {
                            return Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                              decoration: BoxDecoration(color: Colors.white.withOpacity(0.05), borderRadius: BorderRadius.circular(2), border: Border.all(color: Colors.white12)),
                              child: Text(a, style: const TextStyle(color: Colors.white60, fontSize: 9)),
                            );
                          }).toList(),
                        ),
                        const SizedBox(height: 14),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text('${suite['price']} / noche', style: const TextStyle(color: AppTheme.gold, fontSize: 16, fontWeight: FontWeight.bold)),
                            ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.transparent,
                                foregroundColor: AppTheme.gold,
                                side: const BorderSide(color: AppTheme.gold),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                              ),
                              onPressed: () => _openBookingModal(suite),
                              child: const Text('RESERVAR', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                            )
                          ],
                        )
                      ],
                    ),
                  )
                ],
              ),
            );
          }),
        ],
      ),
    );
  }
}
