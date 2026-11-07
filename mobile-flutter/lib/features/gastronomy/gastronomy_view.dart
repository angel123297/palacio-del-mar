import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/theme/app_theme.dart';
import '../../core/providers/booking_provider.dart';
import '../../core/providers/auth_provider.dart';

class GastronomySpaView extends StatefulWidget {
  final VoidCallback? onGoToSuites;
  const GastronomySpaView({super.key, this.onGoToSuites});

  @override
  State<GastronomySpaView> createState() => _GastronomySpaViewState();
}

class _GastronomySpaViewState extends State<GastronomySpaView> {
  String _selectedCategory = 'Todos';

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

  @override
  Widget build(BuildContext context) {
    final bookingProvider = context.watch<BookingProvider>();
    final auth = context.watch<AuthProvider>();

    final filteredMenu = _selectedCategory == 'Todos'
        ? _fullMenu
        : _fullMenu.where((item) => item['category'] == _selectedCategory).toList();

    final totalCount = bookingProvider.totalFoodItems;
    final totalPrice = bookingProvider.totalFoodPrice;

    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'GASTRONOMÍA DE AUTOR',
              style: TextStyle(
                color: AppTheme.gold,
                fontSize: 10,
                letterSpacing: 2,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              'Sabores del Caribe Colombiano',
              style: TextStyle(
                fontFamily: 'serif',
                color: Colors.white,
                fontSize: 22,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Selecciona tus platillos preferidos. Los platillos que elijas se sumarán automáticamente a tu reserva de suite.',
              style: TextStyle(color: Colors.white60, fontSize: 12),
            ),
            const SizedBox(height: 16),

            // Chips de categorías
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: ['Todos', 'Entradas', 'Fuertes', 'Postres', 'Bebidas'].map((cat) {
                  final isSelected = _selectedCategory == cat;
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
                      side: BorderSide(
                        color: isSelected ? AppTheme.gold : Colors.white12,
                      ),
                      onSelected: (_) => setState(() => _selectedCategory = cat),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 16),

            // Tarjetas de Menú Interactivas
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
                      style: const TextStyle(
                        color: AppTheme.gold,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
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
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 15,
                                  ),
                                ),
                              ),
                              Text(
                                item['price']!,
                                style: const TextStyle(
                                  color: AppTheme.gold,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 14,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            item['desc']!,
                            style: const TextStyle(color: Colors.white60, fontSize: 12),
                          ),
                          const SizedBox(height: 12),

                          // Controles interactivos + y -
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
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 15,
                                      fontWeight: FontWeight.bold,
                                    ),
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

            const SizedBox(height: 80), // Margen para la barra inferior flotante
          ],
        ),
      ),
      bottomSheet: totalCount > 0
          ? Container(
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
                            '$totalCount ${totalCount == 1 ? 'platillo seleccionado' : 'platillos seleccionados'}',
                            style: const TextStyle(color: Colors.white70, fontSize: 12),
                          ),
                          Text(
                            '\$ ${totalPrice.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} COP',
                            style: const TextStyle(
                              color: AppTheme.gold,
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
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
                          SnackBar(
                            content: Text(
                              auth.isAuthenticated
                                  ? '¡Platillos listos! Ve al catálogo de Suites para incluirlos en tu reserva.'
                                  : 'Inicia sesión para vincular tu orden con tu reserva.',
                            ),
                            backgroundColor: AppTheme.gold,
                            behavior: SnackBarBehavior.floating,
                          ),
                        );
                      },
                      child: const Text('VER EN RESERVA', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                    ),
                  ],
                ),
              ),
            )
          : null,
    );
  }
}
