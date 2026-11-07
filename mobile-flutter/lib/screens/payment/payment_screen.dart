import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class PaymentScreen extends StatefulWidget {
  final Map<String, dynamic> booking;
  final String token;
  final String baseUrl;

  const PaymentScreen({
    Key? key,
    required this.booking,
    required this.token,
    this.baseUrl = 'https://unbroke-unsized-zandra.ngrok-free.dev/api',
  }) : super(key: key);

  @override
  _PaymentScreenState createState() => _PaymentScreenState();
}

class _PaymentScreenState extends State<PaymentScreen> {
  String _selectedMethod = 'CARD'; // CARD, PSE, NEQUI
  bool _isProcessing = false;

  Future<void> _processPayment() async {
    setState(() => _isProcessing = true);

    try {
      // 1. Iniciar checkout
      final checkoutRes = await http.post(
        Uri.parse('${widget.baseUrl}/payments/checkout'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ${widget.token}',
        },
        body: jsonEncode({
          'bookingId': widget.booking['id'] ?? widget.booking['bookingId'],
          'amount': widget.booking['totalPrice'] ?? widget.booking['totalAmount'],
          'currency': 'COP',
          'paymentMethod': _selectedMethod,
        }),
      );

      final checkoutData = jsonDecode(checkoutRes.body);
      final intentId = checkoutData['intentId'] ?? (checkoutData['data'] != null ? checkoutData['data']['intentId'] : null);

      // 2. Confirmar pago
      final confirmRes = await http.post(
        Uri.parse('${widget.baseUrl}/payments/confirm'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ${widget.token}',
        },
        body: jsonEncode({
          'intentId': intentId,
          'paymentMethodId': 'pm_mobile_simulated',
        }),
      );

      final confirmData = jsonDecode(confirmRes.body);

      if (!mounted) return;

      if (confirmData['status'] == 'SUCCEEDED' || confirmRes.statusCode == 200) {
        // Redirigir a pantalla de confirmación exitosa
        Navigator.pushReplacementNamed(context, '/booking-success', arguments: {
          'bookingCode': confirmData['bookingCode'] ?? widget.booking['code'] ?? widget.booking['bookingCode'] ?? 'PDM-CONFIRMADA',
          'amount': widget.booking['totalPrice'] ?? widget.booking['totalAmount'],
        });
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Pago rechazado por la pasarela')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Error al conectar con la pasarela de pagos')),
        );
      }
    } finally {
      if (mounted) setState(() => _isProcessing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final double amount = ((widget.booking['totalPrice'] ?? widget.booking['totalAmount']) ?? 266000).toDouble();

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(title: const Text('Pasarela de Pagos Segura'), backgroundColor: Colors.transparent),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(color: const Color(0xFFD4AF37)),
                borderRadius: BorderRadius.circular(12),
                color: Colors.black.withOpacity(0.3),
              ),
              child: Column(
                children: [
                  const Text('Total a pagar:', style: TextStyle(color: Colors.grey, fontSize: 16)),
                  const SizedBox(height: 8),
                  Text('\$${amount.toStringAsFixed(0)} COP',
                      style: const TextStyle(color: Color(0xFFD4AF37), fontSize: 28, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
            const SizedBox(height: 24),
            const Text('Selecciona tu método de pago:', style: TextStyle(color: Colors.white, fontSize: 16)),
            const SizedBox(height: 12),
            RadioListTile<String>(
              value: 'CARD',
              groupValue: _selectedMethod,
              activeColor: const Color(0xFFD4AF37),
              title: const Text('Tarjeta de Crédito / Débito', style: TextStyle(color: Colors.white)),
              secondary: const Icon(Icons.credit_card, color: Colors.white),
              onChanged: (v) => setState(() => _selectedMethod = v!),
            ),
            RadioListTile<String>(
              value: 'PSE',
              groupValue: _selectedMethod,
              activeColor: const Color(0xFFD4AF37),
              title: const Text('PSE (Cuentas de Ahorro Colombia)', style: TextStyle(color: Colors.white)),
              secondary: const Icon(Icons.account_balance, color: Colors.white),
              onChanged: (v) => setState(() => _selectedMethod = v!),
            ),
            RadioListTile<String>(
              value: 'NEQUI',
              groupValue: _selectedMethod,
              activeColor: const Color(0xFFD4AF37),
              title: const Text('Nequi / Daviplata', style: TextStyle(color: Colors.white)),
              secondary: const Icon(Icons.phone_android, color: Colors.white),
              onChanged: (v) => setState(() => _selectedMethod = v!),
            ),
            const Spacer(),
            ElevatedButton(
              onPressed: _isProcessing ? null : _processPayment,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFD4AF37),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: _isProcessing
                  ? const CircularProgressIndicator(color: Colors.black)
                  : const Text('Pagar Ahora en COP', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 16)),
            ),
          ],
        ),
      ),
    );
  }
}
