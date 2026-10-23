import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';

class GastronomySpaView extends StatelessWidget {
  const GastronomySpaView({super.key});

  @override
  Widget build(BuildContext context) {
    final List<Map<String, String>> menu = [
      {'num': '01', 'name': 'Ceviche de camarón al coco', 'price': '\$ 68.000', 'desc': 'Camarón fresco, leche de coco, ají dulce y plátano verde.'},
      {'num': '02', 'name': 'Arroz de mariscos del Caribe', 'price': '\$ 125.000', 'desc': 'Arroz meloso con langosta, camarón y calamar de la bahía.'},
      {'num': '03', 'name': 'Posta cartagenera', 'price': '\$ 94.000', 'desc': 'Carne braseada en salsa de panela, vino tinto y especias.'},
      {'num': '04', 'name': 'Cocada horneada', 'price': '\$ 38.000', 'desc': 'Coco caramelizado con helado de maracuyá.'},
    ];

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('GASTRONOMÍA DE AUTOR', style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          const Text('Sabores del Caribe Colombiano', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
          const SizedBox(height: 14),
          ...menu.map((item) {
            return Container(
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(color: AppTheme.cardBg, borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.white10)),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item['num']!, style: const TextStyle(color: AppTheme.gold, fontSize: 15, fontWeight: FontWeight.bold)),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(child: Text(item['name']!, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14))),
                            Text(item['price']!, style: const TextStyle(color: AppTheme.gold, fontWeight: FontWeight.bold, fontSize: 13)),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(item['desc']!, style: const TextStyle(color: Colors.white54, fontSize: 11)),
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
}
