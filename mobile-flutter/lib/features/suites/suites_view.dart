import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/theme/app_theme.dart';
import '../../core/services/api_service.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/providers/booking_provider.dart';
import 'suite_zone_filter.dart';

class SuitesCatalogView extends StatefulWidget {
  const SuitesCatalogView({super.key});

  @override
  State<SuitesCatalogView> createState() => _SuitesCatalogViewState();
}

class _SuitesCatalogViewState extends State<SuitesCatalogView> {
  String _selectedZone = 'Todas';
  int _guests = 1;
  DateTimeRange? _selectedDateRange;
  bool _isLoading = true;
  String? _errorMessage;
  List<Map<String, dynamic>> _catalogSuites = [];
  Set<String>? _availableSuiteIds;
  Map<String, String> _availableZones = {'Todas': 'Todas'};

  @override
  void initState() {
    super.initState();
    _loadSuites();
  }

  Future<void> _loadSuites() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final branchParam = _selectedZone == 'Todas' ? null : _selectedZone;
    final catalogData = await ApiService.getSuites(
      guests: _guests,
      branch: branchParam,
    );

    if (!mounted) return;

    if (catalogData == null) {
      setState(() {
        _errorMessage = 'Error de conexión al cargar las suites. Verifica tu red.';
        _catalogSuites = [];
        _availableSuiteIds = null;
        _isLoading = false;
      });
      return;
    }

    Set<String>? availableIds;
    if (_selectedDateRange != null) {
      final availData = await ApiService.checkAvailability(
        checkIn: _selectedDateRange!.start,
        checkOut: _selectedDateRange!.end,
        guests: _guests,
        branch: branchParam,
      );

      if (!mounted) return;

      if (availData == null) {
        setState(() {
          _errorMessage = 'Error de conexión al consultar disponibilidad.';
          _isLoading = false;
        });
        return;
      }

      final ids = <String>{};
      final rawAvailable = availData['availableSuites'];
      if (rawAvailable is List) {
        for (final item in rawAvailable) {
          if (item is Map) {
            final suiteObj = item['suite'];
            if (suiteObj is Map) {
              final sid = suiteObj['id'] ?? suiteObj['_id'];
              if (sid != null) ids.add(sid.toString());
            } else {
              final sid = item['id'] ?? item['_id'] ?? item['suiteId'];
              if (sid != null) ids.add(sid.toString());
            }
          }
        }
      }
      availableIds = ids;
    }

    final mapped = <Map<String, dynamic>>[];

    for (final s in catalogData) {
      if (s is! Map) continue;
      final id = s['id'] ?? s['_id'];
      final name = s['name'];
      if (id == null || name == null || name.toString().trim().isEmpty) {
        continue;
      }

      final branch = s['branch'];
      final branchSlug = (branch is Map && branch['slug'] != null && branch['slug'].toString().trim().isNotEmpty)
          ? branch['slug'].toString().trim()
          : null;
      final branchLabel = (branch is Map && branch['zone'] != null && branch['zone'].toString().trim().isNotEmpty)
          ? branch['zone'].toString().trim()
          : (branch is Map && branch['name'] != null && branch['name'].toString().trim().isNotEmpty)
              ? branch['name'].toString().trim()
              : branchSlug;

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
        'zone': branchSlug,
        'zoneLabel': branchLabel,
        'category': type,
        'price': priceStr,
        'image': mainImage,
        'amenities': amenitiesList,
      });
    }

    final updatedZones = Map<String, String>.from(_availableZones);
    if (_availableZones.length <= 1 || _selectedZone == 'Todas') {
      updatedZones.addAll(SuiteZoneFilter.extractZones(mapped));
    }

    setState(() {
      _catalogSuites = mapped;
      _availableSuiteIds = availableIds;
      _availableZones = updatedZones;
      if (!_availableZones.containsKey(_selectedZone)) {
        _selectedZone = 'Todas';
      }
      _isLoading = false;
    });
  }

  Future<void> _pickDateRange() async {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final initialRange = _selectedDateRange ??
        DateTimeRange(
          start: today,
          end: today.add(const Duration(days: 1)),
        );

    final picked = await showDateRangePicker(
      context: context,
      firstDate: today,
      lastDate: today.add(const Duration(days: 365)),
      initialDateRange: initialRange,
      builder: (context, child) {
        return Theme(
          data: ThemeData.dark().copyWith(
            colorScheme: const ColorScheme.dark(
              primary: AppTheme.gold,
              onPrimary: Colors.black,
              surface: Color(0xFF1E1E1E),
              onSurface: Colors.white,
            ),
          ),
          child: child!,
        );
      },
    );

    if (picked != null) {
      if (picked.end.isAfter(picked.start)) {
        setState(() {
          _selectedDateRange = picked;
        });
        _loadSuites();
      }
    }
  }

  void _clearDateRange() {
    setState(() {
      _selectedDateRange = null;
    });
    _loadSuites();
  }

  void _openBookingModal(Map<String, dynamic> suite) {
    final auth = context.read<AuthProvider>();
    final checkInDate = _selectedDateRange?.start ?? DateTime.now().add(const Duration(days: 1));
    final checkOutDate = _selectedDateRange?.end ?? DateTime.now().add(const Duration(days: 3));
    bool isSubmitting = false;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg,
      builder: (bottomSheetContext) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            final category = suite['category'];
            final zone = suite['zoneLabel'] ?? suite['zone'];
            final subtitleParts = [
              if (category != null && category.toString().isNotEmpty) category.toString(),
              if (zone != null && zone.toString().isNotEmpty) zone.toString(),
            ].join(' · ');

            final inStr = '${checkInDate.year.toString().padLeft(4, '0')}-${checkInDate.month.toString().padLeft(2, '0')}-${checkInDate.day.toString().padLeft(2, '0')}';
            final outStr = '${checkOutDate.year.toString().padLeft(4, '0')}-${checkOutDate.month.toString().padLeft(2, '0')}-${checkOutDate.day.toString().padLeft(2, '0')}';

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
                      subtitleParts.toUpperCase(),
                      style: const TextStyle(
                        color: AppTheme.gold,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1,
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Icon(Icons.calendar_today_outlined, size: 14, color: AppTheme.gold),
                      const SizedBox(width: 8),
                      Text(
                        'Fechas: $inStr al $outStr',
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.people_outline, size: 14, color: AppTheme.gold),
                      const SizedBox(width: 8),
                      Text(
                        'Huéspedes: $_guests',
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Builder(
                    builder: (context) {
                      final bp = context.watch<BookingProvider>();
                      final foodTotal = bp.totalFoodPrice;
                      final foodCount = bp.totalFoodItems;

                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            suite['price'] != null ? '${suite['price']} / noche' : 'Precio a consultar',
                            style: const TextStyle(
                              color: AppTheme.gold,
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          if (foodCount > 0) ...[
                            const SizedBox(height: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                              decoration: BoxDecoration(
                                color: AppTheme.gold.withValues(alpha: 0.12),
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(color: AppTheme.gold.withValues(alpha: 0.3)),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.restaurant_menu, color: AppTheme.gold, size: 14),
                                  const SizedBox(width: 6),
                                  Text(
                                    '+ $foodCount platillos seleccionados (+\$ ${foodTotal.toInt()} COP)',
                                    style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ],
                      );
                    },
                  ),
                  if (!auth.isAuthenticated) ...[
                    const SizedBox(height: 12),
                    const Text(
                      'Debes iniciar sesión con tu cuenta para confirmar la reserva.',
                      style: TextStyle(color: Colors.orangeAccent, fontSize: 12),
                    ),
                  ],
                  const SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.gold,
                        foregroundColor: Colors.black,
                      ),
                      onPressed: isSubmitting
                          ? null
                          : () async {
                              if (!auth.isAuthenticated) {
                                Navigator.pop(bottomSheetContext);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('Por favor inicia sesión en la pestaña "LLAVE DIGITAL" para reservar.'),
                                    backgroundColor: Colors.orangeAccent,
                                  ),
                                );
                                return;
                              }

                              setModalState(() => isSubmitting = true);

                              final bookingProvider = context.read<BookingProvider>();
                              final foodOrders = bookingProvider.getFormattedFoodOrders();

                              final bookingPayload = {
                                'suiteId': suite['id'],
                                'checkIn': inStr,
                                'checkOut': outStr,
                                'guests': _guests,
                                if (foodOrders.isNotEmpty) 'foodOrders': foodOrders,
                              };

                              final res = await ApiService.createBooking(bookingPayload);
                              if (!bottomSheetContext.mounted) return;
                              setModalState(() => isSubmitting = false);
                              Navigator.pop(bottomSheetContext);

                              if (!mounted) return;

                              if (res['success'] == true) {
                                bookingProvider.clearFoodOrders();
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('¡Reserva creada exitosamente! Revisa tu Llave Digital.'),
                                    backgroundColor: AppTheme.gold,
                                  ),
                                );
                              } else {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(res['message'] ?? 'Error al procesar la reserva'),
                                    backgroundColor: Colors.redAccent,
                                  ),
                                );
                              }
                            },
                      child: isSubmitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(color: Colors.black, strokeWidth: 2),
                            )
                          : const Text('CONFIRMAR RESERVA', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  List<Map<String, dynamic>> _getDisplayedSuites() {
    return SuiteZoneFilter.filterSuites(
      suites: _catalogSuites,
      selectedZone: _selectedZone,
      availableSuiteIds: _availableSuiteIds,
    );
  }

  String _formatDate(DateTime dt) {
    return '${dt.day.toString().padLeft(2, '0')}/${dt.month.toString().padLeft(2, '0')}/${dt.year}';
  }

  @override
  Widget build(BuildContext context) {
    final displayedSuites = _getDisplayedSuites();

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
          const SizedBox(height: 16),
          // Barra de filtros: Adultos y Fechas
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.cardBg,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.white12),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    const Icon(Icons.people_outline, color: AppTheme.gold, size: 20),
                    const SizedBox(width: 8),
                    const Text('Adultos:', style: TextStyle(color: Colors.white70, fontSize: 13)),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(Icons.remove_circle_outline, color: Colors.white70, size: 20),
                      onPressed: _guests > 1
                          ? () {
                              setState(() => _guests--);
                              _loadSuites();
                            }
                          : null,
                    ),
                    Text(
                      '$_guests',
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                    IconButton(
                      icon: const Icon(Icons.add_circle_outline, color: Colors.white70, size: 20),
                      onPressed: () {
                        setState(() => _guests++);
                        _loadSuites();
                      },
                    ),
                  ],
                ),
                const Divider(color: Colors.white10),
                Row(
                  children: [
                    const Icon(Icons.calendar_today_outlined, color: AppTheme.gold, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _selectedDateRange != null
                            ? '${_formatDate(_selectedDateRange!.start)} - ${_formatDate(_selectedDateRange!.end)}'
                            : 'Cualquier fecha',
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (_selectedDateRange != null)
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.white60, size: 18),
                        onPressed: _clearDateRange,
                        tooltip: 'Limpiar fechas',
                      ),
                    TextButton(
                      onPressed: _pickDateRange,
                      child: Text(
                        _selectedDateRange == null ? 'Seleccionar fechas' : 'Cambiar',
                        style: const TextStyle(color: AppTheme.gold, fontSize: 12, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          if (_availableZones.length > 1) ...[
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _availableZones.entries.map((entry) {
                  final zoneSlug = entry.key;
                  final zoneLabel = entry.value;
                  final isSelected = _selectedZone == zoneSlug;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: ChoiceChip(
                      label: Text(zoneLabel),
                      selected: isSelected,
                      onSelected: (_) {
                        setState(() => _selectedZone = zoneSlug);
                        _loadSuites();
                      },
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
          if (_isLoading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: CircularProgressIndicator(color: AppTheme.gold)),
            )
          else if (_errorMessage != null)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline, color: Colors.redAccent, size: 48),
                    const SizedBox(height: 16),
                    Text(
                      _errorMessage!,
                      style: const TextStyle(color: Colors.white70, fontSize: 15),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.gold,
                        foregroundColor: Colors.black,
                      ),
                      onPressed: _loadSuites,
                      child: const Text('Reintentar', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ],
                ),
              ),
            )
          else if (displayedSuites.isEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.hotel_outlined, color: AppTheme.gold, size: 48),
                    const SizedBox(height: 16),
                    Text(
                      _selectedDateRange != null
                          ? 'No hay suites disponibles para esas fechas'
                          : 'No se encontraron suites con los filtros seleccionados',
                      style: const TextStyle(color: Colors.white70, fontSize: 15),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 20),
                    if (_selectedDateRange != null)
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppTheme.gold,
                          foregroundColor: Colors.black,
                        ),
                        onPressed: _clearDateRange,
                        child: const Text('Limpiar fechas', style: TextStyle(fontWeight: FontWeight.bold)),
                      )
                    else
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppTheme.gold,
                          foregroundColor: Colors.black,
                        ),
                        onPressed: _loadSuites,
                        child: const Text('Reintentar', style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                  ],
                ),
              ),
            )
          else
            ...displayedSuites.map((suite) {
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
                clipBehavior: Clip.antiAlias,
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
