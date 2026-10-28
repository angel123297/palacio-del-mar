import 'package:flutter/material.dart';
import '../../screens/filtros_widget.dart';
import '../../core/services/api_service.dart';

class SuitesScreen extends StatefulWidget {
  const SuitesScreen({super.key});

  @override
  State<SuitesScreen> createState() => _SuitesScreenState();
}

class _SuitesScreenState extends State<SuitesScreen> {
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
      final basePrice = s['basePrice'];
      final priceStr = basePrice != null ? '\$ $basePrice' : 'Consultar precio';
      final originalPrice = s['originalPrice'];
      final oldPriceStr = originalPrice != null ? '\$ $originalPrice' : null;
      final rawAmenities = s['amenities'];
      final amenitiesList = (rawAmenities is List)
          ? rawAmenities.map((a) => a.toString().toUpperCase()).toList()
          : <String>[];
      final maxGuests = s['maxGuests'];
      if (maxGuests != null) {
        amenitiesList.add('HASTA $maxGuests HUÉSPEDES');
      }
      final mainImage = (s['mainImage'] != null && s['mainImage'].toString().trim().isNotEmpty)
          ? s['mainImage'].toString().trim()
          : null;
      final isFeatured = s['featured'] == true;
      mapped.add({
        'id': id.toString(),
        'title': name.toString(),
        'zone': branchName,
        'category': type,
        'price': priceStr,
        'oldPrice': oldPriceStr,
        'image': mainImage,
        'recommended': isFeatured,
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

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const darkBg = Color(0xFF0B0C10);
    const cardBg = Color(0xFF141414);

    if (_isLoading) {
      return const Scaffold(
        backgroundColor: darkBg,
        body: Center(
          child: CircularProgressIndicator(color: goldColor),
        ),
      );
    }

    if (_errorMessage != null) {
      return Scaffold(
        backgroundColor: darkBg,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.wifi_off_rounded, color: goldColor, size: 48),
                const SizedBox(height: 16),
                Text(
                  _errorMessage!,
                  style: const TextStyle(color: Colors.white70, fontSize: 14),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 20),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: goldColor,
                    foregroundColor: Colors.black,
                  ),
                  onPressed: _fetchSuites,
                  child: const Text('Reintentar', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          ),
        ),
      );
    }

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
                border: Border.all(color: goldColor.withValues(alpha: 0.3)),
              ),
              child: Column(
                children: [
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
                      children: _availableZones.map((zone) {
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
          if (filteredSuites.isEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 48, horizontal: 24),
                child: Column(
                  children: [
                    const Icon(Icons.hotel_outlined, color: Colors.white38, size: 48),
                    const SizedBox(height: 12),
                    const Text(
                      'No hay suites disponibles',
                      style: TextStyle(color: Colors.white70, fontSize: 15),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: goldColor,
                        foregroundColor: Colors.black,
                      ),
                      onPressed: _fetchSuites,
                      child: const Text('Reintentar', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ],
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.all(16),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final suite = filteredSuites[index];
                    final List<String> amenities = List<String>.from(suite['amenities']);
                    final category = suite['category'];
                    final zone = suite['zone'];
                    final subtitleParts = [category, zone].where((p) => p != null && p.toString().isNotEmpty).join(' · ');
                    final imageUrl = suite['image'];

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
                                child: imageUrl != null
                                    ? Image.network(
                                        imageUrl,
                                        height: 210,
                                        width: double.infinity,
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) => Container(
                                          height: 210,
                                          color: Colors.white10,
                                          child: const Center(child: Icon(Icons.hotel, color: goldColor, size: 48)),
                                        ),
                                      )
                                    : Container(
                                        height: 210,
                                        color: Colors.white10,
                                        child: const Center(child: Icon(Icons.hotel, color: goldColor, size: 48)),
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
                                if (subtitleParts.isNotEmpty) ...[
                                  Text(
                                    subtitleParts.toUpperCase(),
                                    style: const TextStyle(color: goldColor, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                                  ),
                                  const SizedBox(height: 6),
                                ],
                                Text(
                                  suite['title'],
                                  style: const TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                                ),
                                if (amenities.isNotEmpty) ...[
                                  const SizedBox(height: 12),
                                  Wrap(
                                    spacing: 6,
                                    runSpacing: 6,
                                    children: amenities.map((amenity) {
                                      return Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                        decoration: BoxDecoration(
                                          color: Colors.white.withValues(alpha: 0.05),
                                          borderRadius: BorderRadius.circular(2),
                                          border: Border.all(color: Colors.white12),
                                        ),
                                        child: Text(amenity, style: const TextStyle(color: Colors.white60, fontSize: 9, letterSpacing: 0.5)),
                                      );
                                    }).toList(),
                                  ),
                                ],
                                const SizedBox(height: 16),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        if (suite['oldPrice'] != null) ...[
                                          Text(
                                            suite['oldPrice'],
                                            style: const TextStyle(color: Colors.white38, fontSize: 11, decoration: TextDecoration.lineThrough),
                                          ),
                                        ],
                                        Row(
                                          children: [
                                            Text(
                                              suite['price'],
                                              style: const TextStyle(color: goldColor, fontSize: 18, fontWeight: FontWeight.bold),
                                            ),
                                            if (suite['price'] != 'Consultar precio')
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
