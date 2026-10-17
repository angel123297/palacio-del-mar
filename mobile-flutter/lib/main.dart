import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'core/services/api_service.dart';

void main() {
  runApp(const PalacioDelMarApp());
}

class PalacioDelMarApp extends StatelessWidget {
  const PalacioDelMarApp({super.key});

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const darkBg = Color(0xFF0B0C10);

    return MaterialApp(
      title: 'Palacio del Mar - Hotel Boutique',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: darkBg,
        primaryColor: goldColor,
        colorScheme: const ColorScheme.dark(
          primary: goldColor,
          surface: Color(0xFF141414),
        ),
        useMaterial3: true,
      ),
      home: const MainHomeScreen(),
    );
  }
}

class MainHomeScreen extends StatefulWidget {
  const MainHomeScreen({super.key});

  @override
  State<MainHomeScreen> createState() => _MainHomeScreenState();
}

class _MainHomeScreenState extends State<MainHomeScreen> {
  int _currentIndex = 0;

  void _navigateToTab(int index) {
    setState(() => _currentIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);

    final List<Widget> pages = [
      HotelLandingView(onNavigateToSuites: () => _navigateToTab(1)),
      const SuitesCatalogView(),
      const GastronomySpaView(),
      const DigitalKeyView(),
    ];

    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF121212),
        elevation: 0,
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Palacio del Mar',
              style: TextStyle(
                fontFamily: 'serif',
                color: goldColor,
                fontSize: 20,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
            Text(
              'HOTEL BOUTIQUE · CARTAGENA',
              style: TextStyle(color: Colors.white38, fontSize: 8, letterSpacing: 2),
            ),
          ],
        ),
      ),
      body: pages[_currentIndex],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) => setState(() => _currentIndex = index),
        backgroundColor: const Color(0xFF121212),
        selectedItemColor: goldColor,
        unselectedItemColor: Colors.white38,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 10),
        unselectedLabelStyle: const TextStyle(fontSize: 10),
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home_outlined), activeIcon: Icon(Icons.home), label: 'Inicio'),
          BottomNavigationBarItem(icon: Icon(Icons.hotel_outlined), activeIcon: Icon(Icons.hotel), label: 'Suites'),
          BottomNavigationBarItem(icon: Icon(Icons.restaurant_outlined), activeIcon: Icon(Icons.restaurant), label: 'Experiencias'),
          BottomNavigationBarItem(icon: Icon(Icons.key_outlined), activeIcon: Icon(Icons.key), label: 'Llave Digital'),
        ],
      ),
    );
  }
}

// 1. VISTA DE INICIO
class HotelLandingView extends StatefulWidget {
  final VoidCallback onNavigateToSuites;
  const HotelLandingView({super.key, required this.onNavigateToSuites});

  @override
  State<HotelLandingView> createState() => _HotelLandingViewState();
}

class _HotelLandingViewState extends State<HotelLandingView> {
  int _expandedFaq = -1;
  DateTime _checkInDate = DateTime(2026, 10, 18);
  DateTime _checkOutDate = DateTime(2026, 10, 20);
  int _adultsCount = 2;
  String _selectedVibe = 'Cualquiera';

  final _nameContactController = TextEditingController();
  final _emailContactController = TextEditingController();
  final _phoneContactController = TextEditingController();
  final _msgContactController = TextEditingController();

  Future<void> _selectDate(bool isCheckIn) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: isCheckIn ? _checkInDate : _checkOutDate,
      firstDate: DateTime.now(),
      lastDate: DateTime(2028),
      builder: (context, child) {
        return Theme(
          data: ThemeData.dark().copyWith(
            colorScheme: const ColorScheme.dark(primary: Color(0xFFC5A059), onPrimary: Colors.black),
          ),
          child: child!,
        );
      },
    );
    if (picked != null) {
      setState(() {
        if (isCheckIn) {
          _checkInDate = picked;
        } else {
          _checkOutDate = picked;
        }
      });
    }
  }

  void _submitContactForm() async {
    if (_nameContactController.text.isEmpty || _emailContactController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Por favor llena los campos principales de contacto')),
      );
      return;
    }

    final success = await ApiService.sendContactMessage({
      'nombre': _nameContactController.text,
      'email': _emailContactController.text,
      'telefono': _phoneContactController.text,
      'mensaje': _msgContactController.text,
    });

    if (!mounted) return;
    if (success) {
      _nameContactController.clear();
      _emailContactController.clear();
      _phoneContactController.clear();
      _msgContactController.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('¡Mensaje enviado con éxito a Concierge!'),
          backgroundColor: Color(0xFFC5A059),
        ),
      );
    }
  }

  final List<Map<String, String>> _faqs = [
    {
      'q': '¿Cuáles son los horarios de Check-in y Check-out?',
      'a': 'El Check-in está disponible a partir de las 15:00 hrs y el Check-out se realiza hasta las 12:00 hrs del mediodía.'
    },
    {
      'q': '¿Cuál es la política de cancelación de reservas?',
      'a': 'Cancelación gratuita hasta 48 horas antes de la fecha de llegada.'
    },
    {
      'q': '¿Ofrecen servicio de transporte desde el Aeropuerto?',
      'a': 'Sí, ofrecemos traslado privado VIP previo acuerdo con nuestro equipo.'
    },
    {
      'q': '¿Qué formas de pago aceptan?',
      'a': 'Aceptamos tarjetas de crédito, PSE, transferencias y pagos digitales seguros.'
    },
  ];

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const cardBg = Color(0xFF141414);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // BANNER HERO
          Container(
            height: 220,
            width: double.infinity,
            decoration: const BoxDecoration(
              image: DecorationImage(
                image: NetworkImage('https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=1000'),
                fit: BoxFit.cover,
                colorFilter: ColorFilter.mode(Colors.black54, BlendMode.darken),
              ),
            ),
            child: const Padding(
              padding: EdgeInsets.all(20.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('CARTAGENA DE INDIAS · SUCURSALES', style: TextStyle(color: goldColor, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
                  SizedBox(height: 4),
                  Text('Palacio del Mar', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
                  Text('Refugio de diseño exclusivo entre murallas coloniales, Getsemaní y Bocagrande', style: TextStyle(color: Colors.white70, fontSize: 12)),
                ],
              ),
            ),
          ),

          // BARRA DE BÚSQUEDA INTERACTIVA
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: cardBg,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: goldColor.withOpacity(0.3)),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: () => _selectDate(true),
                        child: _searchCol('LLEGADA', '${_checkInDate.day} Oct ${_checkInDate.year}', goldColor),
                      ),
                    ),
                    Container(height: 30, width: 1, color: Colors.white12),
                    Expanded(
                      child: InkWell(
                        onTap: () => _selectDate(false),
                        child: _searchCol('SALIDA', '${_checkOutDate.day} Oct ${_checkOutDate.year}', goldColor),
                      ),
                    ),
                  ],
                ),
                const Divider(color: Colors.white12, height: 20),
                Row(
                  children: [
                    Expanded(
                      child: PopupMenuButton<int>(
                        onSelected: (val) => setState(() => _adultsCount = val),
                        itemBuilder: (context) => [1, 2, 3, 4].map((n) => PopupMenuItem(value: n, child: Text('$n adultos'))).toList(),
                        child: _searchCol('ADULTOS', '$_adultsCount adultos', goldColor),
                      ),
                    ),
                    Container(height: 30, width: 1, color: Colors.white12),
                    Expanded(
                      child: PopupMenuButton<String>(
                        onSelected: (val) => setState(() => _selectedVibe = val),
                        itemBuilder: (context) => ['Cualquiera', 'Romántico', 'Familiar', 'Negocios']
                            .map((v) => PopupMenuItem(value: v, child: Text(v)))
                            .toList(),
                        child: _searchCol('VIBE', _selectedVibe, goldColor),
                      ),
                    ),
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
                    onPressed: widget.onNavigateToSuites,
                    child: const Text('BUSCAR SUITES', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                  ),
                )
              ],
            ),
          ),

          // PREGUNTAS FRECUENTES (FAQ CORREGIDO SIN ASSERTION)
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('PREGUNTAS FRECUENTES', style: TextStyle(color: goldColor, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                const Text('Resuelve tus dudas antes de viajar', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                ...List.generate(_faqs.length, (index) {
                  final isExpanded = _expandedFaq == index;
                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Column(
                      children: [
                        Material(
                          color: Colors.transparent,
                          child: ListTile(
                            title: Text(_faqs[index]['q']!, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
                            trailing: Icon(isExpanded ? Icons.remove : Icons.add, color: goldColor, size: 20),
                            onTap: () => setState(() => _expandedFaq = isExpanded ? -1 : index),
                          ),
                        ),
                        if (isExpanded)
                          Padding(
                            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                            child: Text(_faqs[index]['a']!, style: const TextStyle(color: Colors.white70, fontSize: 12)),
                          ),
                      ],
                    ),
                  );
                }),
              ],
            ),
          ),

          // FORMULARIO DE CONTACTO VIP
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: cardBg,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: goldColor.withOpacity(0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('ATENCIÓN PERSONALIZADA', style: TextStyle(color: goldColor, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  const Text('Eventos, Bodas y Consultas VIP', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 16),
                  TextField(controller: _nameContactController, decoration: const InputDecoration(labelText: 'Nombre Completo', border: OutlineInputBorder())),
                  const SizedBox(height: 12),
                  TextField(controller: _emailContactController, decoration: const InputDecoration(labelText: 'Correo Electrónico', border: OutlineInputBorder())),
                  const SizedBox(height: 12),
                  TextField(controller: _phoneContactController, decoration: const InputDecoration(labelText: 'Teléfono', border: OutlineInputBorder())),
                  const SizedBox(height: 12),
                  TextField(controller: _msgContactController, maxLines: 3, decoration: const InputDecoration(labelText: 'Mensaje o requerimientos especiales', border: OutlineInputBorder())),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: goldColor,
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      onPressed: _submitContactForm,
                      child: const Text('ENVIAR MENSAJE', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _searchCol(String label, String val, Color goldColor) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: Colors.white38, fontSize: 9, letterSpacing: 1, fontWeight: FontWeight.bold)),
          const SizedBox(height: 2),
          Text(val, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

// 2. VISTA CATÁLOGO DE SUITES CON MODAL DE RESERVA HACIA LA BASE DE DATOS
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
    const goldColor = Color(0xFFC5A059);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF141414),
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
              Text('${suite['category']} · ${suite['zone']}', style: const TextStyle(color: goldColor, fontSize: 12)),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Tarifa por noche:', style: TextStyle(color: Colors.white70)),
                  Text(suite['price'], style: const TextStyle(color: goldColor, fontWeight: FontWeight.bold, fontSize: 18)),
                ],
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: goldColor,
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
                        backgroundColor: goldColor,
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
    const goldColor = Color(0xFFC5A059);
    const cardBg = Color(0xFF141414);

    final filtered = _selectedZone == 'Todas'
        ? _suites
        : _suites.where((s) => s['zone'] == _selectedZone).toList();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('NUESTRAS SUITES', style: TextStyle(color: goldColor, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
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
          const SizedBox(height: 16),
          ...filtered.map((suite) {
            final List<String> amenities = List<String>.from(suite['amenities']);
            return Container(
              margin: const EdgeInsets.only(bottom: 20),
              decoration: BoxDecoration(color: cardBg, borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.white10)),
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
                      errorBuilder: (_, __, ___) => Container(height: 190, color: Colors.white10, child: const Center(child: Icon(Icons.hotel, color: goldColor, size: 40))),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('${suite['category']} · ${suite['zone']}'.toUpperCase(), style: const TextStyle(color: goldColor, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 1)),
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
                            Text('${suite['price']} / noche', style: const TextStyle(color: goldColor, fontSize: 16, fontWeight: FontWeight.bold)),
                            ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.transparent,
                                foregroundColor: goldColor,
                                side: const BorderSide(color: goldColor),
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

// 3. VISTA GASTRONOMÍA & SPA
class GastronomySpaView extends StatelessWidget {
  const GastronomySpaView({super.key});

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const cardBg = Color(0xFF141414);

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
          const Text('GASTRONOMÍA DE AUTOR', style: TextStyle(color: goldColor, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          const Text('Sabores del Caribe Colombiano', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
          const SizedBox(height: 14),
          ...menu.map((item) {
            return Container(
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(color: cardBg, borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.white10)),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item['num']!, style: const TextStyle(color: goldColor, fontSize: 15, fontWeight: FontWeight.bold)),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(child: Text(item['name']!, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14))),
                            Text(item['price']!, style: const TextStyle(color: goldColor, fontWeight: FontWeight.bold, fontSize: 13)),
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

// 4. VISTA DIGITAL KEY
class DigitalKeyView extends StatefulWidget {
  const DigitalKeyView({super.key});

  @override
  State<DigitalKeyView> createState() => _DigitalKeyViewState();
}

class _DigitalKeyViewState extends State<DigitalKeyView> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoggedIn = false;
  bool _isLoading = false;

  void _login() async {
    if (_emailController.text.isEmpty || _passwordController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Ingresa correo y contraseña')));
      return;
    }

    setState(() => _isLoading = true);
    final res = await ApiService.login(_emailController.text.trim(), _passwordController.text.trim());
    setState(() => _isLoading = false);

    if (res['success'] == true) {
      setState(() => _isLoggedIn = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const cardBg = Color(0xFF141414);

    if (_isLoggedIn) {
      return Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Container(
            constraints: const BoxConstraints(maxWidth: 440),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: cardBg,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: goldColor.withOpacity(0.4)),
            ),
            child: Column(
              children: [
                const Text('Gran Suite Presidencial', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
                const Text('Centro Histórico · Cartagena', style: TextStyle(color: goldColor, fontSize: 12)),
                const Divider(height: 30, color: Colors.white10),
                const Text('LLAVE DIGITAL DE ACCESO', style: TextStyle(color: goldColor, fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 2)),
                const SizedBox(height: 4),
                const Text('Presenta este código en la cerradura inteligente', style: TextStyle(color: Colors.white38, fontSize: 10)),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
                  child: QrImageView(data: 'RES-8921-PALACIO', version: QrVersions.auto, size: 160),
                ),
                const SizedBox(height: 20),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: goldColor.withOpacity(0.1), borderRadius: BorderRadius.circular(6), border: Border.all(color: goldColor.withOpacity(0.3))),
                  child: const Column(
                    children: [
                      Text('PIN DE HABITACIÓN', style: TextStyle(color: goldColor, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 2)),
                      SizedBox(height: 4),
                      Text('8492', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold, letterSpacing: 6)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Container(
          constraints: const BoxConstraints(maxWidth: 400),
          padding: const EdgeInsets.all(28),
          decoration: BoxDecoration(
            color: cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: goldColor.withOpacity(0.3)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Palacio del Mar', style: TextStyle(fontFamily: 'serif', color: goldColor, fontSize: 24, fontWeight: FontWeight.bold)),
              const Text('LLAVE DIGITAL & ACCESO A SUITES', style: TextStyle(color: Colors.white54, fontSize: 10, letterSpacing: 1.5)),
              const SizedBox(height: 24),
              TextField(
                controller: _emailController,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Correo Electrónico', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _passwordController,
                obscureText: true,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Contraseña', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 20),
              _isLoading
                  ? const CircularProgressIndicator(color: goldColor)
                  : ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: goldColor,
                        foregroundColor: Colors.black,
                        minimumSize: const Size.fromHeight(48),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                      ),
                      onPressed: _login,
                      child: const Text('INICIAR SESIÓN', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                    ),
            ],
          ),
        ),
      ),
    );
  }
}
