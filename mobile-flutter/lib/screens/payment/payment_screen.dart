import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'receipt_screen.dart';

class PaymentScreen extends StatefulWidget {
  final Map<String, dynamic> booking;
  final String token;
  final String baseUrl;

  const PaymentScreen({
    super.key,
    required this.booking,
    required this.token,
    this.baseUrl = 'https://unbroke-unsized-zandra.ngrok-free.dev/api',
  });

  @override
  State<PaymentScreen> createState() => _PaymentScreenState();
}

class _PaymentScreenState extends State<PaymentScreen> {
  String _selectedMethod = 'CARD'; // CARD, PSE, NEQUI
  bool _isProcessing = false;

  // Temporizador de 15 minutos (900 segundos)
  int _secondsRemaining = 900;
  Timer? _countdownTimer;

  // Formulario Tarjeta
  final _cardNameController = TextEditingController();
  final _cardNumberController = TextEditingController();
  final _cardExpiryController = TextEditingController();
  final _cardCvvController = TextEditingController();

  // Formulario PSE
  String _selectedBank = '';
  final List<Map<String, String>> _banks = [
    {'id': 'bancolombia', 'name': 'Bancolombia'},
    {'id': 'davivienda', 'name': 'Davivienda'},
    {'id': 'bogota', 'name': 'Banco de Bogotá'},
    {'id': 'bbva', 'name': 'BBVA Colombia'},
    {'id': 'occidente', 'name': 'Banco de Occidente'},
    {'id': 'daviplata', 'name': 'DaviPlata'},
    {'id': 'colpatria', 'name': 'Scotiabank Colpatria'},
  ];

  // Formulario Nequi
  final _nequiPhoneController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _startTimer();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _cardNameController.dispose();
    _cardNumberController.dispose();
    _cardExpiryController.dispose();
    _cardCvvController.dispose();
    _nequiPhoneController.dispose();
    super.dispose();
  }

  void _startTimer() {
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_secondsRemaining > 0) {
        if (mounted) {
          setState(() => _secondsRemaining--);
        }
      } else {
        timer.cancel();
      }
    });
  }

  String get _formattedTime {
    final minutes = (_secondsRemaining ~/ 60).toString().padLeft(2, '0');
    final seconds = (_secondsRemaining % 60).toString().padLeft(2, '0');
    return '$minutes : $seconds';
  }

  Map<String, dynamic> get _targetBooking {
    if (widget.booking['data'] is Map<String, dynamic>) {
      return widget.booking['data'] as Map<String, dynamic>;
    }
    return widget.booking;
  }

  Future<void> _processPayment() async {
    if (_secondsRemaining <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('El tiempo para pagar esta reserva ha expirado.')),
      );
      return;
    }

    setState(() => _isProcessing = true);

    try {
      final bk = _targetBooking;
      final bId = bk['id'] ?? bk['_id'] ?? bk['bookingId'] ?? widget.booking['id'] ?? widget.booking['_id'];
      final rawAmount = bk['totalPrice'] ?? bk['totalAmount'] ?? bk['balanceDue'] ?? widget.booking['totalPrice'];
      double amountVal = 250000;
      if (rawAmount is num) {
        amountVal = rawAmount.toDouble();
      } else if (rawAmount is String) {
        amountVal = double.tryParse(rawAmount.replaceAll(RegExp(r'[^0-9.]'), '')) ?? 250000;
      }

      if (bId == null || bId.toString().isEmpty) {
        throw Exception('Reserva no especificada');
      }

      // 1. Iniciar checkout con backend Spring Boot
      final checkoutRes = await http.post(
        Uri.parse('${widget.baseUrl}/payments/checkout'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ${widget.token}',
          'ngrok-skip-browser-warning': 'true',
        },
        body: jsonEncode({
          'bookingId': bId,
          'method': _selectedMethod.toLowerCase(),
        }),
      );

      final checkoutJson = jsonDecode(checkoutRes.body);
      final checkoutData = checkoutJson['data'] ?? checkoutJson;
      final intentId = checkoutData['intentId'] ?? checkoutJson['intentId'];

      if (intentId == null) {
        throw Exception(checkoutJson['message'] ?? 'No se pudo generar la orden de cobro');
      }

      // 2. Determinar token simulado de pasarela según tarjeta introducida
      String paymentToken = 'pm_card_visa';
      final cardRaw = _cardNumberController.text.replaceAll(' ', '').trim();
      if (cardRaw.endsWith('0002') || cardRaw.endsWith('9995') || cardRaw.endsWith('069')) {
        paymentToken = 'pm_card_declined';
      }

      // 3. Confirmar pago
      final confirmRes = await http.post(
        Uri.parse('${widget.baseUrl}/payments/confirm'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ${widget.token}',
          'ngrok-skip-browser-warning': 'true',
        },
        body: jsonEncode({
          'intentId': intentId,
          'paymentToken': paymentToken,
        }),
      );

      final confirmJson = jsonDecode(confirmRes.body);
      if (confirmRes.statusCode != 200 && confirmRes.statusCode != 201) {
        throw Exception(confirmJson['message'] ?? 'Error al confirmar la transacción');
      }

      final confirmData = confirmJson['data'] ?? confirmJson;

      if (!mounted) return;

      final updatedBooking = confirmData['booking'] is Map ? confirmData['booking'] : widget.booking;
      final bookingCode = (updatedBooking['bookingCode'] ?? updatedBooking['code'] ?? bk['bookingCode'] ?? bk['code'] ?? 'PDM-CONFIRMADO').toString();
      final smartPin = (updatedBooking['smartLockPin'] ?? bk['smartLockPin'] ?? '182786').toString();

      final fullBookingMap = Map<String, dynamic>.from(bk);
      fullBookingMap['smartLockPin'] = smartPin;
      fullBookingMap['status'] = 'CONFIRMED';
      fullBookingMap['paymentStatus'] = 'paid';

      // Redirigir al Comprobante & Voucher nativo
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => BookingReceiptScreen(
            booking: fullBookingMap,
            amount: amountVal,
            bookingCode: bookingCode,
          ),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error al procesar pago: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isProcessing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bk = _targetBooking;
    final rawAmount = bk['totalPrice'] ?? bk['totalAmount'] ?? bk['balanceDue'] ?? widget.booking['totalPrice'];
    double amount = 250000;
    if (rawAmount is num) {
      amount = rawAmount.toDouble();
    } else if (rawAmount is String) {
      amount = double.tryParse(rawAmount.replaceAll(RegExp(r'[^0-9.]'), '')) ?? 250000;
    }

    final isExpired = _secondsRemaining <= 0;

    return Scaffold(
      backgroundColor: const Color(0xFF0D0D0D),
      appBar: AppBar(
        title: const Text('Pasarela de Pago Segura', style: TextStyle(color: Color(0xFFD4AF37), fontSize: 16)),
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Contador de 15 Minutos (TTL)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: isExpired ? Colors.red.withOpacity(0.15) : const Color(0xFFD4AF37).withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isExpired ? Colors.redAccent : const Color(0xFFD4AF37).withOpacity(0.4),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    isExpired ? 'Tiempo expirado' : 'Tiempo restante para pagar tu reserva:',
                    style: TextStyle(
                      color: isExpired ? Colors.redAccent : Colors.white,
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    _formattedTime,
                    style: TextStyle(
                      fontFamily: 'monospace',
                      color: isExpired ? Colors.redAccent : const Color(0xFFD4AF37),
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 2,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),

            const Text(
              'Elige tu método de pago',
              style: TextStyle(
                fontFamily: 'serif',
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),

            // Selector horizontal de método de pago (3 columnas)
            Row(
              children: [
                Expanded(child: _methodTab('CARD', 'Tarjeta de Crédito', Icons.credit_card)),
                const SizedBox(width: 8),
                Expanded(child: _methodTab('PSE', 'PSE / Bancos', Icons.account_balance)),
                const SizedBox(width: 8),
                Expanded(child: _methodTab('NEQUI', 'Nequi', Icons.phone_android)),
              ],
            ),
            const SizedBox(height: 16),

            // FORMULARIO DINÁMICO SEGÚN MÉTODO
            if (_selectedMethod == 'CARD') _buildCardForm(amount, isExpired),
            if (_selectedMethod == 'PSE') _buildPseForm(amount, isExpired),
            if (_selectedMethod == 'NEQUI') _buildNequiForm(amount, isExpired),

            const SizedBox(height: 16),
            const Center(
              child: Text(
                '🔒 Transacción protegida y cifrada con TLS 1.3',
                style: TextStyle(color: Colors.white38, fontSize: 11),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _methodTab(String key, String label, IconData icon) {
    final isSelected = _selectedMethod == key;
    return InkWell(
      onTap: () => setState(() => _selectedMethod = key),
      borderRadius: BorderRadius.circular(10),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 6),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFFD4AF37).withOpacity(0.12) : const Color(0xFF161616),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? const Color(0xFFD4AF37) : Colors.white12,
            width: isSelected ? 1.5 : 1,
          ),
        ),
        child: Column(
          children: [
            Icon(icon, color: isSelected ? const Color(0xFFD4AF37) : Colors.white60, size: 26),
            const SizedBox(height: 6),
            Text(
              label,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: isSelected ? Colors.white : Colors.white60,
                fontSize: 11,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCardForm(double amount, bool isExpired) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF161616),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Detalles de la Tarjeta',
                style: TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.bold),
              ),
              Row(
                children: [
                  _badge('VISA', Colors.blue.shade900),
                  const SizedBox(width: 4),
                  _badge('MC', Colors.red.shade900),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          _fieldLabel('Titular de la tarjeta'),
          _customInput(_cardNameController, 'Como aparece en la tarjeta'),
          const SizedBox(height: 12),
          _fieldLabel('Número de Tarjeta'),
          _customInput(_cardNumberController, '0000 0000 0000 0000', keyboardType: TextInputType.number),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _fieldLabel('Expiración'),
                    _customInput(_cardExpiryController, 'MM/AA', keyboardType: TextInputType.datetime),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _fieldLabel('CVV / CVC'),
                    _customInput(_cardCvvController, '•••', obscureText: true, keyboardType: TextInputType.number),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          _payButton('PAGAR \$ ${amount.toInt()}', const Color(0xFFD4AF37), Colors.black, isExpired),
        ],
      ),
    );
  }

  Widget _buildPseForm(double amount, bool isExpired) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF161616),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Center(
            child: Text(
              'Pago Seguro en Línea (PSE)',
              style: TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.bold),
            ),
          ),
          const SizedBox(height: 14),
          _fieldLabel('Selecciona tu banco'),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: Colors.black.withOpacity(0.5),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: Colors.white24),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<String>(
                isExpanded: true,
                value: _selectedBank.isEmpty ? null : _selectedBank,
                hint: const Text('- Elige un banco -', style: TextStyle(color: Colors.white38, fontSize: 13)),
                dropdownColor: const Color(0xFF1E1E1E),
                items: _banks.map((b) {
                  return DropdownMenuItem<String>(
                    value: b['id'],
                    child: Text(b['name']!, style: const TextStyle(color: Colors.white, fontSize: 13)),
                  );
                }).toList(),
                onChanged: (val) => setState(() => _selectedBank = val ?? ''),
              ),
            ),
          ),
          const SizedBox(height: 12),
          const Text(
            'Al continuar, serás redirigido a la pasarela segura de tu banco para confirmar la transacción.',
            style: TextStyle(color: Colors.white54, fontSize: 11),
          ),
          const SizedBox(height: 20),
          _payButton('IR AL BANCO A PAGAR \$ ${amount.toInt()}', const Color(0xFFD4AF37), Colors.black, isExpired),
        ],
      ),
    );
  }

  Widget _buildNequiForm(double amount, bool isExpired) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF161616),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          const Text(
            'Nequi',
            style: TextStyle(
              color: Color(0xFFFF007F),
              fontSize: 26,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            'Ingresa tu número celular registrado en Nequi para recibir la notificación de pago en tu app.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white70, fontSize: 12),
          ),
          const SizedBox(height: 16),
          Align(alignment: Alignment.centerLeft, child: _fieldLabel('Número Celular Nequi')),
          _customInput(_nequiPhoneController, 'Ej: 300 123 4567', keyboardType: TextInputType.phone),
          const SizedBox(height: 20),
          _payButton('ENVIAR NOTIFICACIÓN A MI NEQUI', const Color(0xFFFF007F), Colors.white, isExpired),
        ],
      ),
    );
  }

  Widget _fieldLabel(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text(text, style: const TextStyle(color: Colors.white54, fontSize: 11)),
    );
  }

  Widget _customInput(
    TextEditingController controller,
    String placeholder, {
    bool obscureText = false,
    TextInputType keyboardType = TextInputType.text,
  }) {
    return TextField(
      controller: controller,
      obscureText: obscureText,
      keyboardType: keyboardType,
      style: const TextStyle(color: Colors.white, fontSize: 13),
      decoration: InputDecoration(
        hintText: placeholder,
        hintStyle: const TextStyle(color: Colors.white30, fontSize: 12),
        filled: true,
        fillColor: Colors.black.withOpacity(0.5),
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(6),
          borderSide: const BorderSide(color: Colors.white12),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(6),
          borderSide: const BorderSide(color: Color(0xFFD4AF37)),
        ),
      ),
    );
  }

  Widget _payButton(String label, Color bg, Color fg, bool isExpired) {
    return SizedBox(
      width: double.infinity,
      height: 48,
      child: ElevatedButton(
        onPressed: (isExpired || _isProcessing) ? null : _processPayment,
        style: ElevatedButton.styleFrom(
          backgroundColor: bg,
          foregroundColor: fg,
          disabledBackgroundColor: Colors.grey.shade800,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
        ),
        child: _isProcessing
            ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(color: Colors.black, strokeWidth: 2),
              )
            : Text(
                label,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, letterSpacing: 0.5),
              ),
      ),
    );
  }

  Widget _badge(String title, Color bg) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(3)),
      child: Text(
        title,
        style: const TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.bold),
      ),
    );
  }
}
