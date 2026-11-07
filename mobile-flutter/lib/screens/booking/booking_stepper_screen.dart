import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';
import 'dart:convert';
import '../../core/providers/auth_provider.dart';
import '../payment/payment_screen.dart';

class BookingStepperScreen extends StatefulWidget {
  final String suiteId;
  final String suiteName;
  final double pricePerNightCOP;
  final String token;
  final String baseUrl;

  const BookingStepperScreen({
    Key? key,
    required this.suiteId,
    required this.suiteName,
    required this.pricePerNightCOP,
    required this.token,
    this.baseUrl = 'https://unbroke-unsized-zandra.ngrok-free.dev/api',
  }) : super(key: key);

  @override
  _BookingStepperScreenState createState() => _BookingStepperScreenState();
}

class _BookingStepperScreenState extends State<BookingStepperScreen> {
  int _currentStep = 0;
  bool _isLoading = false;

  // Paso 1: Fechas
  DateTime _checkIn = DateTime.now().add(const Duration(days: 1));
  DateTime _checkOut = DateTime.now().add(const Duration(days: 3));

  // Paso 2: Huéspedes y agregados
  int _guests = 2;
  int _children = 0;
  bool _needsCrib = false;
  bool _petFriendly = false;

  // Paso 3: Servicios Adicionales
  bool _airportPickup = false;
  final double _airportPriceCOP = 60000;
  bool _breakfastIncluded = false;
  final double _breakfastPerNightCOP = 35000;

  // Paso 4: Huésped titular
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final auth = context.read<AuthProvider>();
      if (auth.currentUserName != null && auth.currentUserName!.trim().isNotEmpty) {
        _nameController.text = auth.currentUserName!.trim();
      }
    });
  }

  int get _nights {
    final diff = _checkOut.difference(_checkIn).inDays;
    return diff > 0 ? diff : 1;
  }

  double get _baseTotal => _nights * widget.pricePerNightCOP;
  double get _extraServicesTotal {
    double total = 0;
    if (_airportPickup) total += _airportPriceCOP;
    if (_breakfastIncluded) total += (_breakfastPerNightCOP * _guests * _nights);
    return total;
  }
  double get _platformFee => (_baseTotal + _extraServicesTotal) * 0.08; // 8% comisión
  double get _grandTotalCOP => _baseTotal + _extraServicesTotal + _platformFee;

  Future<void> _submitBooking() async {
    setState(() => _isLoading = true);

    final auth = context.read<AuthProvider>();
    final guestEmail = auth.currentUserEmail ?? '';

    final payload = {
      'suiteId': widget.suiteId,
      'checkIn': _checkIn.toIso8601String().split('T')[0],
      'checkOut': _checkOut.toIso8601String().split('T')[0],
      'guests': _guests,
      'children': _children,
      'needsCrib': _needsCrib,
      'petFriendly': _petFriendly,
      'airportPickup': _airportPickup,
      'guestName': _nameController.text.trim(),
      'guestEmail': guestEmail,
      'guestPhone': _phoneController.text.trim(),
      'foodOrders': _breakfastIncluded ? [{'item': 'Desayuno Buffet Ruta del Sabor', 'quantity': _guests * _nights}] : [],
    };

    try {
      final response = await http.post(
        Uri.parse('${widget.baseUrl}/bookings'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ${widget.token}',
        },
        body: jsonEncode(payload),
      );

      if (!mounted) return;

      if (response.statusCode == 200 || response.statusCode == 201) {
        final booking = jsonDecode(response.body);
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => PaymentScreen(
              booking: booking is Map<String, dynamic> ? booking : {'id': booking.toString()},
              token: widget.token,
              baseUrl: widget.baseUrl,
            ),
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al crear reserva: ${response.body}')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Error de conexión al procesar la reserva')),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: Text('Reservar: ${widget.suiteName}'),
        backgroundColor: Colors.transparent,
      ),
      body: Stepper(
        currentStep: _currentStep,
        onStepContinue: () {
          if (_currentStep < 3) {
            setState(() => _currentStep++);
          } else {
            _submitBooking();
          }
        },
        onStepCancel: () {
          if (_currentStep > 0) setState(() => _currentStep--);
        },
        controlsBuilder: (context, details) {
          return Padding(
            padding: const EdgeInsets.only(top: 16.0),
            child: Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : details.onStepContinue,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFD4AF37),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: _isLoading
                        ? const CircularProgressIndicator(color: Colors.black)
                        : Text(_currentStep == 3 ? 'Confirmar y Pagar' : 'Siguiente',
                            style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
                  ),
                ),
                if (_currentStep > 0) ...[
                  const SizedBox(width: 12),
                  OutlinedButton(
                    onPressed: details.onStepCancel,
                    child: const Text('Atrás', style: TextStyle(color: Colors.white)),
                  ),
                ],
              ],
            ),
          );
        },
        steps: [
          // PASO 1: FECHAS
          Step(
            title: const Text('Fechas y Estadía', style: TextStyle(color: Colors.white)),
            isActive: _currentStep >= 0,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Noches seleccionadas: $_nights', style: const TextStyle(color: Color(0xFFD4AF37), fontSize: 16)),
                const SizedBox(height: 8),
                ListTile(
                  tileColor: Colors.white.withOpacity(0.05),
                  title: const Text('Check-in', style: TextStyle(color: Colors.white)),
                  subtitle: Text('${_checkIn.day}/${_checkIn.month}/${_checkIn.year}', style: const TextStyle(color: Colors.grey)),
                  trailing: const Icon(Icons.calendar_today, color: Color(0xFFD4AF37)),
                  onTap: () async {
                    final picked = await showDatePicker(
                      context: context,
                      initialDate: _checkIn,
                      firstDate: DateTime.now(),
                      lastDate: DateTime.now().add(const Duration(days: 365)),
                    );
                    if (picked != null) setState(() => _checkIn = picked);
                  },
                ),
                const SizedBox(height: 8),
                ListTile(
                  tileColor: Colors.white.withOpacity(0.05),
                  title: const Text('Check-out', style: TextStyle(color: Colors.white)),
                  subtitle: Text('${_checkOut.day}/${_checkOut.month}/${_checkOut.year}', style: const TextStyle(color: Colors.grey)),
                  trailing: const Icon(Icons.calendar_today, color: Color(0xFFD4AF37)),
                  onTap: () async {
                    final picked = await showDatePicker(
                      context: context,
                      initialDate: _checkOut,
                      firstDate: _checkIn.add(const Duration(days: 1)),
                      lastDate: DateTime.now().add(const Duration(days: 365)),
                    );
                    if (picked != null) setState(() => _checkOut = picked);
                  },
                ),
              ],
            ),
          ),

          // PASO 2: HUÉSPEDES Y AGREGADOS
          Step(
            title: const Text('Huéspedes y Opciones', style: TextStyle(color: Colors.white)),
            isActive: _currentStep >= 1,
            content: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('Adultos:', style: TextStyle(color: Colors.white)),
                    DropdownButton<int>(
                      value: _guests,
                      dropdownColor: const Color(0xFF1E293B),
                      items: [1, 2, 3, 4, 5].map((e) => DropdownMenuItem(value: e, child: Text('$e', style: const TextStyle(color: Colors.white)))).toList(),
                      onChanged: (v) => setState(() => _guests = v!),
                    ),
                  ],
                ),
                SwitchListTile(
                  title: const Text('Requiere cuna para bebé', style: TextStyle(color: Colors.white)),
                  value: _needsCrib,
                  activeColor: const Color(0xFFD4AF37),
                  onChanged: (v) => setState(() => _needsCrib = v),
                ),
                SwitchListTile(
                  title: const Text('Mascota / Pet-Friendly', style: TextStyle(color: Colors.white)),
                  value: _petFriendly,
                  activeColor: const Color(0xFFD4AF37),
                  onChanged: (v) => setState(() => _petFriendly = v),
                ),
              ],
            ),
          ),

          // PASO 3: SERVICIOS Y COMIDA
          Step(
            title: const Text('Servicios y Traslado', style: TextStyle(color: Colors.white)),
            isActive: _currentStep >= 2,
            content: Column(
              children: [
                SwitchListTile(
                  title: const Text('Traslado privado desde Aeropuerto', style: TextStyle(color: Colors.white)),
                  subtitle: const Text('+\$60.000 COP tarifa fija', style: TextStyle(color: Colors.grey)),
                  value: _airportPickup,
                  activeColor: const Color(0xFFD4AF37),
                  onChanged: (v) => setState(() => _airportPickup = v),
                ),
                SwitchListTile(
                  title: const Text('Desayuno Buffet Ruta del Sabor', style: TextStyle(color: Colors.white)),
                  subtitle: const Text('+\$35.000 COP por persona / noche', style: TextStyle(color: Colors.grey)),
                  value: _breakfastIncluded,
                  activeColor: const Color(0xFFD4AF37),
                  onChanged: (v) => setState(() => _breakfastIncluded = v),
                ),
              ],
            ),
          ),

          // PASO 4: DATOS DEL TITULAR Y RESUMEN
          Step(
            title: const Text('Datos y Resumen', style: TextStyle(color: Colors.white)),
            isActive: _currentStep >= 3,
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: _nameController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(labelText: 'Nombre Completo', labelStyle: TextStyle(color: Colors.grey)),
                ),
                TextField(
                  controller: _phoneController,
                  style: const TextStyle(color: Colors.white),
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(labelText: 'Teléfono de Contacto', labelStyle: TextStyle(color: Colors.grey)),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: Colors.white.withOpacity(0.05), borderRadius: BorderRadius.circular(8)),
                  child: Column(
                    children: [
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                        Text('Estadía ($_nights noches):', style: const TextStyle(color: Colors.grey)),
                        Text('\$${_baseTotal.toStringAsFixed(0)} COP', style: const TextStyle(color: Colors.white)),
                      ]),
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                        const Text('Servicios adicionales:', style: TextStyle(color: Colors.grey)),
                        Text('\$${_extraServicesTotal.toStringAsFixed(0)} COP', style: const TextStyle(color: Colors.white)),
                      ]),
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                        const Text('Tarifa de servicio (8%):', style: TextStyle(color: Colors.grey)),
                        Text('\$${_platformFee.toStringAsFixed(0)} COP', style: const TextStyle(color: Colors.white)),
                      ]),
                      const Divider(color: Colors.grey),
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                        const Text('TOTAL RESERVA:', style: TextStyle(color: Color(0xFFD4AF37), fontWeight: FontWeight.bold, fontSize: 16)),
                        Text('\$${_grandTotalCOP.toStringAsFixed(0)} COP', style: const TextStyle(color: Color(0xFFD4AF37), fontWeight: FontWeight.bold, fontSize: 16)),
                      ]),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
