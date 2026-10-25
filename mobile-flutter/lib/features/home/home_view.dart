import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';
import '../../core/services/api_service.dart';

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
          data: AppTheme.darkTheme,
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
          backgroundColor: AppTheme.gold,
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
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
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
                  Text('CARTAGENA DE INDIAS · SUCURSALES', style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
                  SizedBox(height: 4),
                  Text('Palacio del Mar', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
                  Text('Refugio de diseño exclusivo entre murallas coloniales, Getsemaní y Bocagrande', style: TextStyle(color: Colors.white70, fontSize: 12)),
                ],
              ),
            ),
          ),
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppTheme.cardBg,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppTheme.gold.withOpacity(0.3)),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: () => _selectDate(true),
                        child: _searchCol('LLEGADA', '${_checkInDate.day} Oct ${_checkInDate.year}'),
                      ),
                    ),
                    Container(height: 30, width: 1, color: Colors.white12),
                    Expanded(
                      child: InkWell(
                        onTap: () => _selectDate(false),
                        child: _searchCol('SALIDA', '${_checkOutDate.day} Oct ${_checkOutDate.year}'),
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
                        child: _searchCol('ADULTOS', '$_adultsCount adultos'),
                      ),
                    ),
                    Container(height: 30, width: 1, color: Colors.white12),
                    Expanded(
                      child: PopupMenuButton<String>(
                        onSelected: (val) => setState(() => _selectedVibe = val),
                        itemBuilder: (context) => ['Cualquiera', 'Romántico', 'Familiar', 'Negocios']
                            .map((v) => PopupMenuItem(value: v, child: Text(v)))
                            .toList(),
                        child: _searchCol('VIBE', _selectedVibe),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.gold,
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
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('PREGUNTAS FRECUENTES', style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                const Text('Resuelve tus dudas antes de viajar', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                ...List.generate(_faqs.length, (index) {
                  final isExpanded = _expandedFaq == index;
                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    decoration: BoxDecoration(
                      color: AppTheme.cardBg,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Column(
                      children: [
                        Material(
                          color: Colors.transparent,
                          child: ListTile(
                            title: Text(_faqs[index]['q']!, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
                            trailing: Icon(isExpanded ? Icons.remove : Icons.add, color: AppTheme.gold, size: 20),
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
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppTheme.cardBg,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppTheme.gold.withOpacity(0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('ATENCIÓN PERSONALIZADA', style: TextStyle(color: AppTheme.gold, fontSize: 10, letterSpacing: 2, fontWeight: FontWeight.bold)),
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
                        backgroundColor: AppTheme.gold,
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

  Widget _searchCol(String label, String val) {
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
