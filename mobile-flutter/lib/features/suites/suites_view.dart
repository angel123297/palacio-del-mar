import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/theme/app_theme.dart';
import '../../core/providers/booking_provider.dart';
import '../../core/services/api_service.dart';

class SuitesCatalogView extends StatefulWidget {
  const SuitesCatalogView({super.key});

  @override
  State<SuitesCatalogView> createState() => _SuitesCatalogViewState();
}

class _SuitesCatalogViewState extends State<SuitesCatalogView> {
  String _selectedZone = 'Todas';
  bool _isLoading = true;
  String? _errorMessage;
  List<Map<String, dynamic>> _suites = [];
  List<String> _availableZones = ['Todas'];

  @override
  void initState() {
    super.initState();
    _fetchSuites();
  }

  Future<void> _fetchSuites() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    final data = await ApiService.getSuites();
    if (!mounted) return;

    if (data == null) {
      setState(() {
        _errorMessage = 'Error al cargar las suites. Verifica tu conexión.';
        _suites = [];
        _isLoading = false;
      });
      return;
    }

    final mapped = <Map<String, dynamic>>[];
    final zonesSet = <String>{'Todas'};

    for (final s in data) {
      if (s is! Map) continue;
      final id = s['id'] ?? s['_id'];
      final name = s['name'];
      if (id == null || name == null || name.toString().trim().isEmpty) {
        continue;
      }

      final branch = s['branch'];
      final branchName = (branch is Map && branch['name'] != null && branch['name'].toString().trim().isNotEmpty)
          ? branch['name'].toString().trim()
          : null;
      if (branchName != null) {
        zonesSet.add(branchName);
      }

      final type = (s['type'] != null && s['type'].toString().trim().isNotEmpty)
          ? s['type'].toString().trim().toUpperCase()
          : null;

      final basePrice = s['basePrice'] ?? s['price'];
      final priceStr = basePrice != null ? '\$ $basePrice' : null;

      final rawAmenities = s['amenities'];
      final amenitiesList = (rawAmenities is List)
          ? rawAmenities
              .where((a) => a != null && a.toString().trim().isNotEmpty)
              .map((a) => a.toString().trim().toUpperCase())
              .toList()
          : <String>[];

      final mainImage = (s['mainImage'] != null && s['mainImage'].toString().trim().isNotEmpty)
          ? s['mainImage'].toString().trim()
          : null;

      mapped.add({
        'id': id.toString(),
        'title': name.toString(),
        'zone': branchName,
        'category': type,
        'price': priceStr,
        'image': mainImage,
        'amenities': amenitiesList,
      });
    }

    setState(() {
      _suites = mapped;
      _availableZones = zonesSet.toList();
      if (!_availableZones.contains(_selectedZone)) {
        _selectedZone = 'Todas';
      }
      _isLoading = false;
    });
  }

  void _openBookingModal(Map<String, dynamic> suite) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg,
      builder: (context) {
        final category = suite['category'];
        final zone = suite['zone'];
        final subtitleParts = [
          if (category != null && category.toString().isNotEmpty) category.toString(),
          if (zone != null && zone.toString().isNotEmpty) zone.toString(),
        ].join(' • ');

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
              Text(
                suite['title'],
                style: const TextStyle(
                  fontFamily: 'serif',
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              if (subtitleParts.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  subtitleParts,
                  style: const TextStyle(color: AppTheme.gold, fontSize: 12),
                ),
              ],
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Tarifa por noche:', style: TextStyle(color: Colors.white70)),
                  Text(
                    suite['price'] ?? 'Consultar precio',
                    style: const TextStyle(
                      color: AppTheme.gold,
                      fontWeight: FontWeight.bold,
                      fontSize: 18,
                    ),
                  ),
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
                  onPressed: () {
                    context.read<BookingProvider>().addSuiteToCart(suite);
                    Navigator.pop(context);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('"${suite['title']}" añadida a la reserva'),
                        backgroundColor: AppTheme.gold,
                      ),
                    );
                  },
                  child: const Text(
                    'AGREGAR A LA RESERVA',
                    style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1),
                  ),
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
    if (_isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(40.0),
          child: CircularProgressIndicator(color: AppTheme.gold),
        ),
      );
    }

    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.wifi_off_rounded, color: AppTheme.gold, size: 48),
              const SizedBox(height: 16),
              Text(
                _errorMessage!,
                style: const TextStyle(color: Colors.white70, fontSize: 14),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.gold,
                  foregroundColor: Colors.black,
                ),
                onPressed: _fetchSuites,
                child: const Text('Reintentar', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
      );
    }

    final filtered = _selectedZone == 'Todas'
        ? _suites
        : _suites.where((s) => s['zone'] == _selectedZone).toList();

    if (_suites.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.hotel_outlined, color: AppTheme.gold, size: 48),
              const SizedBox(height: 16),
              const Text(
                'No hay suites disponibles en este momento',
                style: TextStyle(color: Colors.white70, fontSize: 15),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.gold,
                  foregroundColor: Colors.black,
                ),
                onPressed: _fetchSuites,
                child: const Text('Reintentar', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'NUESTRAS SUITES',
            style: TextStyle(
              color: AppTheme.gold,
              fontSize: 10,
              letterSpacing: 2,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Elige tu Palacio en Cartagena',
            style: TextStyle(
              fontFamily: 'serif',
              color: Colors.white,
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),
          if (_availableZones.length > 1) ...[
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _availableZones.map((zone) {
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
          ],
          const SizedBox(height: 16),
          if (filtered.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 32.0),
              child: Center(
                child: Text(
                  'No se encontraron suites en esta ubicación',
                  style: TextStyle(color: Colors.white54, fontSize: 14),
                ),
              ),
            )
          else
            ...filtered.map((suite) {
              final List<String> amenities = List<String>.from(suite['amenities'] ?? []);
              final category = suite['category'];
              final zone = suite['zone'];
              final subtitleParts = [
                if (category != null && category.toString().isNotEmpty) category.toString(),
                if (zone != null && zone.toString().isNotEmpty) zone.toString(),
              ].join(' • ');

              return Container(
                margin: const EdgeInsets.only(bottom: 20),
                decoration: BoxDecoration(
                  color: AppTheme.cardBg,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.white10),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    ClipRRect(
                      borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
                      child: suite['image'] != null
                          ? Image.network(
                              suite['image'],
                              height: 190,
                              width: double.infinity,
                              fit: BoxFit.cover,
                              errorBuilder: (context, error, stackTrace) {
                                return _imagePlaceholder();
                              },
                            )
                          : _imagePlaceholder(),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          if (subtitleParts.isNotEmpty) ...[
                            Text(
                              subtitleParts.toUpperCase(),
                              style: const TextStyle(
                                color: AppTheme.gold,
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1,
                              ),
                            ),
                            const SizedBox(height: 4),
                          ],
                          Text(
                            suite['title'],
                            style: const TextStyle(
                              fontFamily: 'serif',
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          if (amenities.isNotEmpty) ...[
                            const SizedBox(height: 10),
                            Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              children: amenities.map((a) => Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                                decoration: BoxDecoration(
                                  color: Colors.white.withValues(alpha: 0.05),
                                  borderRadius: BorderRadius.circular(2),
                                  border: Border.all(color: Colors.white12),
                                ),
                                child: Text(a, style: const TextStyle(color: Colors.white60, fontSize: 9)),
                              )).toList(),
                            ),
                          ],
                          const SizedBox(height: 14),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                suite['price'] != null ? '${suite['price']} / noche' : 'Consultar precio',
                                style: const TextStyle(
                                  color: AppTheme.gold,
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.transparent,
                                  foregroundColor: AppTheme.gold,
                                  side: const BorderSide(color: AppTheme.gold),
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                ),
                                onPressed: () => _openBookingModal(suite),
                                child: const Text('RESERVAR', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _imagePlaceholder() {
    return Container(
      height: 190,
      width: double.infinity,
      color: const Color(0xFF1A1A1A),
      child: const Center(
        child: Icon(Icons.hotel_outlined, color: AppTheme.gold, size: 48),
      ),
    );
  }
}
