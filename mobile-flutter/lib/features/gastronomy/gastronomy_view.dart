import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/theme/app_theme.dart';
import '../../core/providers/booking_provider.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/services/api_service.dart';
import '../../core/config/api_config.dart';
import '../../screens/payment/payment_screen.dart';

class GastronomySpaView extends StatefulWidget {
  final VoidCallback? onGoToSuites;
  const GastronomySpaView({super.key, this.onGoToSuites});

  @override
  State<GastronomySpaView> createState() => _GastronomySpaViewState();
}

class _GastronomySpaViewState extends State<GastronomySpaView> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  String _selectedFoodCategory = 'Todos';
  String _selectedExpCategory = 'Todas';

  // Catálogo de Gastronomía
  static const List<Map<String, dynamic>> _fullMenu = [
    {
      'num': '01',
      'name': 'Ceviche de camarón al coco',
      'category': 'Entradas',
      'price': '\$ 68.000',
      'priceRaw': 68000.0,
      'desc': 'Camarón fresco macerado en leche de coco natural, ají dulce y chips de plátano verde.',
    },
    {
      'num': '02',
      'name': 'Arroz de mariscos del Caribe',
      'category': 'Fuertes',
      'price': '\$ 125.000',
      'priceRaw': 125000.0,
      'desc': 'Arroz meloso cocinado con langosta fresca, camarón tigre y calamar de la bahía.',
    },
    {
      'num': '03',
      'name': 'Posta negra cartagenera',
      'category': 'Fuertes',
      'price': '\$ 94.000',
      'priceRaw': 94000.0,
      'desc': 'Carne de res braseada 12 horas en salsa de panela orgánica, vino tinto y especias locales.',
    },
    {
      'num': '04',
      'name': 'Pargo rojo frito con patacones',
      'category': 'Fuertes',
      'price': '\$ 88.000',
      'priceRaw': 88000.0,
      'desc': 'Pargo entero crujiente acompañado de arroz con coco, patacones y ensalada de aguacate.',
    },
    {
      'num': '05',
      'name': 'Cocada horneada al horno de leña',
      'category': 'Postres',
      'price': '\$ 38.000',
      'priceRaw': 38000.0,
      'desc': 'Coco fresco caramelizado a la leña servido con helado artesanal de maracuyá.',
    },
    {
      'num': '06',
      'name': 'Mousse de mango biche & corozo',
      'category': 'Postres',
      'price': '\$ 34.000',
      'priceRaw': 34000.0,
      'desc': 'Equilibrio refrescante de frutos tropicales con crumble de almendras tostadas.',
    },
    {
      'num': '07',
      'name': 'Cóctel de autor Palacio del Mar',
      'category': 'Bebidas',
      'price': '\$ 48.000',
      'priceRaw': 48000.0,
      'desc': 'Ron añejo colombiano infusionado con frutos caribeños, hierbabuena y soda artesanal.',
    },
  ];

  // Experiencias de Lujo & Aventura
  List<Map<String, dynamic>> _experiences = [];
  bool _isLoadingExp = true;

  // Catálogo base offline de Experiencias de Lujo
  static const List<Map<String, dynamic>> _defaultExperiences = [
    {
      'id': 'exp-01',
      'name': 'Islas del Rosario & Snorkel VIP',
      'category': 'Aventura',
      'price': 348000.0,
      'shortDescription': 'Navegación privada a las cristalinas aguas del Parque Natural con snorkel y almuerzo.',
      'image': 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80',
    },
    {
      'id': 'exp-02',
      'name': 'Catamarán de Lujo al Atardecer',
      'category': 'Romance',
      'price': 290000.0,
      'shortDescription': 'Brisa marina, barra libre premium y música en vivo navegando por la bahía colonial.',
      'image': 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800&auto=format&fit=crop&q=80',
    },
    {
      'id': 'exp-03',
      'name': 'Ritual Spa & Masaje de Oro',
      'category': 'Relajación',
      'price': 310000.0,
      'shortDescription': 'Experiencia holística de 90 minutos con aceites esenciales botánicos y piedras volcánicas.',
      'image': 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=800&auto=format&fit=crop&q=80',
    },
    {
      'id': 'exp-04',
      'name': 'Cena Romántica en la Muralla',
      'category': 'Romance',
      'price': 184000.0,
      'shortDescription': 'Menú degustación exclusivo al atardecer sobre las históricas murallas de Cartagena.',
      'image': 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80',
    },
    {
      'id': 'exp-05',
      'name': 'Cata de Ron & Café Colombiano',
      'category': 'Cultural',
      'price': 195000.0,
      'shortDescription': 'Maridaje sensorial con chocolates artesanales, rones añejos y cafés de altura.',
      'image': 'https://images.unsplash.com/photo-1511920170033-f8396924c348?w=800&auto=format&fit=crop&q=80',
    },
    {
      'id': 'exp-06',
      'name': 'Taller de Alta Cocina Caribeña',
      'category': 'Gastronomía',
      'price': 220000.0,
      'shortDescription': 'Clase privada para preparar cazuela de mariscos y arroz con coco junto al Chef Ejecutivo.',
      'image': 'https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&auto=format&fit=crop&q=80',
    },
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadExperiences();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadExperiences() async {
    setState(() => _isLoadingExp = true);
    final data = await ApiService.getExperiences();
    if (!mounted) return;

    setState(() {
      _isLoadingExp = false;
      if (data.isNotEmpty) {
        _experiences = data;
      } else {
        _experiences = List.from(_defaultExperiences);
      }
    });
  }

  String _formatCOP(num amount) {
    final str = amount.toInt().toString();
    return str.replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.');
  }

  void _openStandaloneExperienceModal(Map<String, dynamic> exp) {
    final auth = context.read<AuthProvider>();
    DateTime selectedDate = DateTime.now().add(const Duration(days: 1));
    int persons = 1;
    final nameCtrl = TextEditingController(text: auth.currentUserName ?? '');
    final emailCtrl = TextEditingController(text: auth.currentUserEmail ?? '');
    final phoneCtrl = TextEditingController();
    bool submitting = false;

    final rawPrice = exp['price'] ?? 200000;
    double pricePerPerson = 200000;
    if (rawPrice is num) {
      pricePerPerson = rawPrice.toDouble();
    } else if (rawPrice is String) {
      final cleaned = rawPrice.replaceAll(RegExp(r'[^0-9]'), '');
      pricePerPerson = double.tryParse(cleaned) ?? 200000;
    }

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF141210),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        side: BorderSide(color: AppTheme.gold, width: 1),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            final total = pricePerPerson * persons;

            return Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 20,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 20,
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'RESERVA INMEDIATA DE EXPERIENCIA',
                          style: TextStyle(color: AppTheme.gold, fontSize: 11, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close, color: Colors.white70),
                          onPressed: () => Navigator.pop(ctx),
                        ),
                      ],
                    ),
                    Text(
                      exp['name']?.toString() ?? '',
                      style: const TextStyle(color: Colors.white, fontSize: 18, fontFamily: 'serif', fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      exp['shortDescription']?.toString() ?? exp['description']?.toString() ?? '',
                      style: const TextStyle(color: Colors.white60, fontSize: 12),
                    ),
                    const Divider(color: Colors.white12, height: 24),

                    // Selector de Fecha
                    Row(
                      children: [
                        Expanded(
                          child: InkWell(
                            onTap: () async {
                              final picked = await showDatePicker(
                                context: context,
                                initialDate: selectedDate,
                                firstDate: DateTime.now(),
                                lastDate: DateTime.now().add(const Duration(days: 365)),
                              );
                              if (picked != null) {
                                setModalState(() => selectedDate = picked);
                              }
                            },
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              decoration: BoxDecoration(
                                color: Colors.black26,
                                border: Border.all(color: Colors.white24),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Text('Fecha Actividad', style: TextStyle(color: Colors.white54, fontSize: 10)),
                                      Text(
                                        '${selectedDate.day}/${selectedDate.month}/${selectedDate.year}',
                                        style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold),
                                      ),
                                    ],
                                  ),
                                  const Icon(Icons.calendar_month, color: AppTheme.gold, size: 18),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        // Personas
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                          decoration: BoxDecoration(
                            color: Colors.black26,
                            border: Border.all(color: Colors.white24),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Row(
                            children: [
                              IconButton(
                                icon: const Icon(Icons.remove, color: AppTheme.gold, size: 16),
                                constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
                                padding: EdgeInsets.zero,
                                onPressed: persons > 1 ? () => setModalState(() => persons--) : null,
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 8),
                                child: Text('$persons pers.', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                              ),
                              IconButton(
                                icon: const Icon(Icons.add, color: AppTheme.gold, size: 16),
                                constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
                                padding: EdgeInsets.zero,
                                onPressed: persons < 10 ? () => setModalState(() => persons++) : null,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    TextField(
                      controller: nameCtrl,
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                      decoration: const InputDecoration(
                        labelText: 'Nombre Completo',
                        labelStyle: TextStyle(color: Colors.white54, fontSize: 12),
                        border: OutlineInputBorder(),
                        contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: emailCtrl,
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                      decoration: const InputDecoration(
                        labelText: 'Correo Electrónico',
                        labelStyle: TextStyle(color: Colors.white54, fontSize: 12),
                        border: OutlineInputBorder(),
                        contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: phoneCtrl,
                      keyboardType: TextInputType.phone,
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                      decoration: const InputDecoration(
                        labelText: 'Teléfono / WhatsApp',
                        labelStyle: TextStyle(color: Colors.white54, fontSize: 12),
                        border: OutlineInputBorder(),
                        contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      ),
                    ),
                    const SizedBox(height: 16),

                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Total a pagar:', style: TextStyle(color: Colors.white54, fontSize: 11)),
                            Text('\$ ${_formatCOP(total)} COP', style: const TextStyle(color: AppTheme.gold, fontSize: 18, fontWeight: FontWeight.bold)),
                          ],
                        ),
                        ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppTheme.gold,
                            foregroundColor: Colors.black,
                            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                          ),
                          onPressed: submitting
                              ? null
                              : () async {
                                  if (nameCtrl.text.trim().isEmpty || emailCtrl.text.trim().isEmpty) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(content: Text('Por favor ingresa nombre y correo')),
                                    );
                                    return;
                                  }

                                  setModalState(() => submitting = true);
                                  final expId = (exp['id'] ?? exp['_id'] ?? '').toString();

                                  final res = await ApiService.bookExperienceOnly(
                                    experienceId: expId,
                                    date: selectedDate,
                                    quantity: persons,
                                    guestName: nameCtrl.text.trim(),
                                    guestEmail: emailCtrl.text.trim(),
                                    guestPhone: phoneCtrl.text.trim(),
                                  );

                                  if (!context.mounted) return;
                                  Navigator.pop(ctx);

                                  if (res['success'] == true) {
                                    final bookingData = res['data'] is Map ? (res['data'] as Map<String, dynamic>) : <String, dynamic>{};

                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (_) => PaymentScreen(
                                          booking: bookingData,
                                          token: auth.token ?? '',
                                          baseUrl: ApiConfig.baseUrl,
                                        ),
                                      ),
                                    );
                                  } else {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text(res['message'] ?? 'Error al procesar reserva de actividad'),
                                        backgroundColor: Colors.redAccent,
                                      ),
                                    );
                                  }
                                },
                          child: submitting
                              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(color: Colors.black, strokeWidth: 2))
                              : const Text('PAGAR AHORA →', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final bookingProvider = context.watch<BookingProvider>();

    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(48),
        child: Container(
          color: const Color(0xFF141210),
          child: TabBar(
            controller: _tabController,
            indicatorColor: AppTheme.gold,
            labelColor: AppTheme.gold,
            unselectedLabelColor: Colors.white54,
            labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12, letterSpacing: 0.8),
            tabs: const [
              Tab(icon: Icon(Icons.star_border, size: 16), text: 'EXPERIENCIAS DE LUJO'),
              Tab(icon: Icon(Icons.restaurant, size: 16), text: 'GASTRONOMÍA & SPA'),
            ],
          ),
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // PESTAÑA 1: EXPERIENCIAS DE LUJO
          _buildLuxuryExperiencesTab(bookingProvider),

          // PESTAÑA 2: GASTRONOMÍA & SPA
          _buildGastronomyTab(bookingProvider),
        ],
      ),
      bottomSheet: (bookingProvider.totalFoodItems > 0 || bookingProvider.totalExperienceCount > 0)
          ? _buildCartSummarySheet(bookingProvider)
          : null,
    );
  }

  // --- VISTA PESTAÑA 1: EXPERIENCIAS DE LUJO ---
  Widget _buildLuxuryExperiencesTab(BookingProvider bookingProvider) {
    final filteredExp = _selectedExpCategory == 'Todas'
        ? _experiences
        : _experiences.where((e) {
            final cat = (e['category'] ?? '').toString().toLowerCase();
            return cat.contains(_selectedExpCategory.toLowerCase());
          }).toList();

    return RefreshIndicator(
      onRefresh: _loadExperiences,
      color: AppTheme.gold,
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'MOMENTOS INOLVIDABLES',
              style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            const Text(
              'Experiencias de Lujo & Aventura',
              style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 6),
            const Text(
              'Vívelas con reserva individual o agrégalas directamente a tu reserva de suite.',
              style: TextStyle(color: Colors.white60, fontSize: 12),
            ),
            const SizedBox(height: 16),

            // Chips de Categoría
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: ['Todas', 'Aventura', 'Romance', 'Relajación', 'Cultural', 'Gastronomía'].map((cat) {
                  final isSel = _selectedExpCategory == cat;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: FilterChip(
                      selected: isSel,
                      label: Text(cat),
                      labelStyle: TextStyle(color: isSel ? Colors.black : Colors.white70, fontSize: 11, fontWeight: isSel ? FontWeight.bold : FontWeight.normal),
                      backgroundColor: AppTheme.cardBg,
                      selectedColor: AppTheme.gold,
                      checkmarkColor: Colors.black,
                      side: BorderSide(color: isSel ? AppTheme.gold : Colors.white12),
                      onSelected: (_) => setState(() => _selectedExpCategory = cat),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 16),

            if (_isLoadingExp)
              const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator(color: AppTheme.gold)))
            else
              ...filteredExp.map((exp) {
                final id = (exp['id'] ?? exp['_id'] ?? exp['name'] ?? '').toString();
                final inCartQty = bookingProvider.getExperienceQuantity(id);

                final rawPrice = exp['price'] ?? 0;
                double priceVal = 0;
                if (rawPrice is num) {
                  priceVal = rawPrice.toDouble();
                } else if (rawPrice is String) {
                  final cleaned = rawPrice.replaceAll(RegExp(r'[^0-9]'), '');
                  priceVal = double.tryParse(cleaned) ?? 0;
                }

                return Container(
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: AppTheme.cardBg,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: inCartQty > 0 ? AppTheme.gold : Colors.white12,
                      width: inCartQty > 0 ? 1.5 : 1.0,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Imagen con categoría
                      Stack(
                        children: [
                          ClipRRect(
                            borderRadius: const BorderRadius.vertical(top: Radius.circular(9)),
                            child: Image.network(
                              (exp['image'] ?? exp['mainImage'] ?? 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800').toString(),
                              height: 140,
                              width: double.infinity,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(
                                height: 140,
                                color: Colors.white10,
                                child: const Center(child: Icon(Icons.star_outline, color: AppTheme.gold, size: 36)),
                              ),
                            ),
                          ),
                          Positioned(
                            top: 10,
                            right: 10,
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: Colors.black87,
                                borderRadius: BorderRadius.circular(20),
                                border: Border.all(color: AppTheme.gold.withValues(alpha: 0.6)),
                              ),
                              child: Text(
                                (exp['category'] ?? 'Exclusivo').toString().toUpperCase(),
                                style: const TextStyle(color: AppTheme.gold, fontSize: 9, fontWeight: FontWeight.bold, letterSpacing: 0.8),
                              ),
                            ),
                          ),
                        ],
                      ),
                      Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              exp['name']?.toString() ?? '',
                              style: const TextStyle(color: Colors.white, fontSize: 16, fontFamily: 'serif', fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              exp['shortDescription']?.toString() ?? exp['description']?.toString() ?? '',
                              style: const TextStyle(color: Colors.white60, fontSize: 12),
                            ),
                            const SizedBox(height: 12),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text('Por persona', style: TextStyle(color: Colors.white38, fontSize: 10)),
                                    Text(
                                      '\$ ${_formatCOP(priceVal)} COP',
                                      style: const TextStyle(color: AppTheme.gold, fontSize: 15, fontWeight: FontWeight.bold),
                                    ),
                                  ],
                                ),
                                Row(
                                  children: [
                                    // Botón para reservar solo esta experiencia
                                    OutlinedButton(
                                      style: OutlinedButton.styleFrom(
                                        foregroundColor: Colors.white,
                                        side: const BorderSide(color: Colors.white24),
                                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                                      ),
                                      onPressed: () => _openStandaloneExperienceModal(exp),
                                      child: const Text('RESERVAR', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold)),
                                    ),
                                    const SizedBox(width: 8),
                                    // Botón para sumar a la reserva de suite con control +/-
                                    if (inCartQty > 0) ...[
                                      Container(
                                        decoration: BoxDecoration(
                                          border: Border.all(color: AppTheme.gold.withValues(alpha: 0.5)),
                                          borderRadius: BorderRadius.circular(4),
                                        ),
                                        child: IconButton(
                                          icon: const Icon(Icons.remove, color: AppTheme.gold, size: 16),
                                          constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                                          padding: EdgeInsets.zero,
                                          tooltip: 'Quitar de la reserva',
                                          onPressed: () {
                                            bookingProvider.removeExperience(id);
                                            ScaffoldMessenger.of(context).showSnackBar(
                                              SnackBar(
                                                content: Text('Se quitó "${exp['name']}" de la reserva'),
                                                backgroundColor: Colors.white24,
                                                duration: const Duration(seconds: 1),
                                                behavior: SnackBarBehavior.floating,
                                              ),
                                            );
                                          },
                                        ),
                                      ),
                                      Padding(
                                        padding: const EdgeInsets.symmetric(horizontal: 10),
                                        child: Text(
                                          inCartQty.toString(),
                                          style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.bold),
                                        ),
                                      ),
                                    ],
                                    ElevatedButton.icon(
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: inCartQty > 0 ? AppTheme.gold : const Color(0xFF262626),
                                        foregroundColor: inCartQty > 0 ? Colors.black : AppTheme.gold,
                                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                      ),
                                      icon: Icon(Icons.add, size: 14, color: inCartQty > 0 ? Colors.black : AppTheme.gold),
                                      label: Text(
                                        inCartQty > 0 ? 'MÁS' : '+ SUITE',
                                        style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold),
                                      ),
                                      onPressed: () {
                                        bookingProvider.addExperience(exp);
                                        ScaffoldMessenger.of(context).showSnackBar(
                                          SnackBar(
                                            content: Text('¡"${exp['name']}" agregada a la reserva!'),
                                            backgroundColor: AppTheme.gold,
                                            duration: const Duration(seconds: 1),
                                            behavior: SnackBarBehavior.floating,
                                          ),
                                        );
                                      },
                                    ),
                                  ],
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

            const SizedBox(height: 80),
          ],
        ),
      ),
    );
  }

  // --- VISTA PESTAÑA 2: GASTRONOMÍA & SPA ---
  Widget _buildGastronomyTab(BookingProvider bookingProvider) {
    final filteredMenu = _selectedFoodCategory == 'Todos'
        ? _fullMenu
        : _fullMenu.where((item) => item['category'] == _selectedFoodCategory).toList();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'GASTRONOMÍA DE AUTOR',
            style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 4),
          const Text(
            'Sabores del Caribe Colombiano',
            style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            'Selecciona tus platillos preferidos. Se sumarán automáticamente cuando reserves cualquier suite.',
            style: TextStyle(color: Colors.white60, fontSize: 12),
          ),
          const SizedBox(height: 16),

          // Chips de categorías
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: ['Todos', 'Entradas', 'Fuertes', 'Postres', 'Bebidas'].map((cat) {
                final isSelected = _selectedFoodCategory == cat;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    selected: isSelected,
                    label: Text(cat),
                    labelStyle: TextStyle(
                      color: isSelected ? Colors.black : Colors.white70,
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      fontSize: 12,
                    ),
                    backgroundColor: AppTheme.cardBg,
                    selectedColor: AppTheme.gold,
                    checkmarkColor: Colors.black,
                    side: BorderSide(color: isSelected ? AppTheme.gold : Colors.white12),
                    onSelected: (_) => setState(() => _selectedFoodCategory = cat),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 16),

          // Tarjetas de Menú
          ...filteredMenu.map((item) {
            final name = item['name'] as String;
            final qty = bookingProvider.getQuantity(name);

            return Container(
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.cardBg,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: qty > 0 ? AppTheme.gold.withValues(alpha: 0.8) : Colors.white10,
                  width: qty > 0 ? 1.5 : 1.0,
                ),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item['num']!,
                    style: const TextStyle(color: AppTheme.gold, fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Text(
                                name,
                                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
                              ),
                            ),
                            Text(
                              item['price']!,
                              style: const TextStyle(color: AppTheme.gold, fontWeight: FontWeight.bold, fontSize: 14),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(item['desc']!, style: const TextStyle(color: Colors.white60, fontSize: 12)),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            if (qty > 0) ...[
                              Container(
                                decoration: BoxDecoration(
                                  border: Border.all(color: AppTheme.gold.withValues(alpha: 0.5)),
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: IconButton(
                                  icon: const Icon(Icons.remove, color: AppTheme.gold, size: 16),
                                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                                  padding: EdgeInsets.zero,
                                  onPressed: () => bookingProvider.removeFoodItem(name),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 14),
                                child: Text(
                                  qty.toString(),
                                  style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold),
                                ),
                              ),
                            ],
                            ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: qty > 0 ? AppTheme.gold : const Color(0xFF262626),
                                foregroundColor: qty > 0 ? Colors.black : AppTheme.gold,
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                              ),
                              icon: Icon(Icons.add, size: 14, color: qty > 0 ? Colors.black : AppTheme.gold),
                              label: Text(
                                qty == 0 ? 'AGREGAR' : 'MÁS',
                                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                              ),
                              onPressed: () => bookingProvider.addFoodItem(item),
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

          const SizedBox(height: 80),
        ],
      ),
    );
  }

  // --- BARRA INFERIOR FLOTANTE CON RESUMEN ---
  Widget _buildCartSummarySheet(BookingProvider bookingProvider) {
    final foodCount = bookingProvider.totalFoodItems;
    final expCount = bookingProvider.totalExperienceCount;
    final combinedTotal = bookingProvider.totalFoodPrice + bookingProvider.totalExperiencePrice;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      decoration: BoxDecoration(
        color: const Color(0xFF161616),
        border: const Border(top: BorderSide(color: AppTheme.gold, width: 1.5)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.6),
            blurRadius: 10,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '$foodCount ${foodCount == 1 ? 'platillo' : 'platillos'}, $expCount ${expCount == 1 ? 'experiencia' : 'experiencias'}',
                    style: const TextStyle(color: Colors.white70, fontSize: 11),
                  ),
                  Text(
                    '\$ ${_formatCOP(combinedTotal)} COP',
                    style: const TextStyle(color: AppTheme.gold, fontSize: 17, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.gold,
                foregroundColor: Colors.black,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
              ),
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('¡Selección guardada! Ve a Suites y pulsa RESERVAR para ver el desglose.'),
                    backgroundColor: AppTheme.gold,
                    behavior: SnackBarBehavior.floating,
                  ),
                );
              },
              child: const Text('LISTO EN RESERVAS', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
            ),
          ],
        ),
      ),
    );
  }
}
