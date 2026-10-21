import 'package:flutter/material.dart';

class GastronomyScreen extends StatelessWidget {
  const GastronomyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const darkCard = Color(0xFF141414);

    final List<Map<String, String>> menu = [
      {'num': '01', 'name': 'Ceviche de camarón al coco', 'price': '\$ 68.000', 'desc': 'Camarón fresco, leche de coco, ají dulce y plátano verde.'},
      {'num': '02', 'name': 'Arroz de mariscos del Caribe', 'price': '\$ 125.000', 'desc': 'Arroz meloso con langosta, camarón y calamar de la bahía.'},
      {'num': '03', 'name': 'Posta cartagenera', 'price': '\$ 94.000', 'desc': 'Carne braseada en salsa de panela, vino tinto y especias.'},
      {'num': '04', 'name': 'Cocada horneada', 'price': '\$ 38.000', 'desc': 'Coco caramelizado con helado de maracuyá.'},
    ];

    return Scaffold(
      backgroundColor: const Color(0xFF0D0D0D),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('GASTRONOMÍA DE AUTOR', style: TextStyle(color: goldColor, fontSize: 11, letterSpacing: 2, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          const Text('Sabores del Caribe Colombiano', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          ...menu.map((item) {
            return Container(
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: darkCard,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.white10),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item['num']!, style: const TextStyle(color: goldColor, fontSize: 16, fontWeight: FontWeight.bold)),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(child: Text(item['name']!, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15))),
                            Text(item['price']!, style: const TextStyle(color: goldColor, fontWeight: FontWeight.bold, fontSize: 14)),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(item['desc']!, style: const TextStyle(color: Colors.white54, fontSize: 12)),
                      ],
                    ),
                  ),
                ],
              ),
            );
          }),
          const SizedBox(height: 28),
          const Text('BIENESTAR & SPA CARIBE', style: TextStyle(color: goldColor, fontSize: 11, letterSpacing: 2, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          const Text('Rituales Ancestrales', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: darkCard,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: goldColor.withOpacity(0.3)),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Tratamientos con barro volcánico, aceites autóctonos y masaje relajante frente a la bahía.', style: TextStyle(color: Colors.white70, fontSize: 13)),
                SizedBox(height: 12),
                Row(
                  children: [
                    Icon(Icons.spa_outlined, color: goldColor, size: 18),
                    SizedBox(width: 8),
                    Text('Baños de barro · Rituales caribeños', style: TextStyle(color: goldColor, fontSize: 12, fontWeight: FontWeight.bold)),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
