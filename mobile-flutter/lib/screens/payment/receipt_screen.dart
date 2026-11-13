import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../core/theme/app_theme.dart';

class BookingReceiptScreen extends StatelessWidget {
  final Map<String, dynamic> booking;
  final double amount;
  final String bookingCode;

  const BookingReceiptScreen({
    super.key,
    required this.booking,
    required this.amount,
    required this.bookingCode,
  });

  @override
  Widget build(BuildContext context) {
    final suiteName = (booking['suiteName'] ?? booking['suite']?['name'] ?? 'Suite Palacio del Mar').toString();
    final rawPin = booking['smartLockPin']?.toString().trim();
    final smartPin = (rawPin != null && rawPin.isNotEmpty) ? rawPin : 'Pendiente';
    final checkIn = (booking['checkIn'] ?? booking['checkInDate'] ?? '').toString();
    final checkOut = (booking['checkOut'] ?? booking['checkOutDate'] ?? '').toString();
    final guestName = (booking['guestName'] ?? booking['user']?['name'] ?? booking['guestDetails']?['fullName'] ?? 'Huésped Palacio').toString();

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text('Comprobante & Voucher', style: TextStyle(color: AppTheme.gold, fontSize: 16)),
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
        automaticallyImplyLeading: false,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        child: Column(
          children: [
            // Header con Check Verde
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF161616),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.gold.withValues(alpha: 0.3)),
              ),
              child: Column(
                children: [
                  const Icon(Icons.check_circle_outline, color: Color(0xFF2ECC71), size: 54),
                  const SizedBox(height: 8),
                  const Text(
                    '¡PAGO CONFIRMADO!',
                    style: TextStyle(
                      color: Color(0xFF2ECC71),
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    'Palacio del Mar · Cartagena de Indias',
                    style: TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'N° ${bookingCode.toUpperCase()}',
                    style: const TextStyle(
                      fontFamily: 'serif',
                      color: AppTheme.gold,
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Total Pagado: \$ ${amount.toInt()} COP',
                    style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Tarjeta de Pase Inteligente y PIN
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFF1E1E1E),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.gold.withValues(alpha: 0.4)),
              ),
              child: Column(
                children: [
                  const Text(
                    'PASE DE ACCESO INTELIGENTE',
                    style: TextStyle(
                      color: AppTheme.gold,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 2,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    suiteName,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontFamily: 'serif',
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'PIN DE CERRADURA ELECTRÓNICA',
                    style: TextStyle(color: Colors.white60, fontSize: 10, letterSpacing: 1),
                  ),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                    decoration: BoxDecoration(
                      color: Colors.black,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppTheme.gold),
                    ),
                    child: Text(
                      smartPin,
                      style: TextStyle(
                        fontFamily: smartPin == 'Pendiente' ? 'sans-serif' : 'monospace',
                        color: AppTheme.gold,
                        fontSize: smartPin == 'Pendiente' ? 20 : 28,
                        fontWeight: FontWeight.bold,
                        letterSpacing: smartPin == 'Pendiente' ? 2 : 6,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  // Código QR
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: QrImageView(
                      data: bookingCode,
                      version: QrVersions.auto,
                      size: 140,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Presenta este código en recepción para Check-In Exprés',
                    style: TextStyle(color: Colors.white54, fontSize: 10),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Detalles de Estadía
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF161616),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.white12),
              ),
              child: Column(
                children: [
                  _detailRow('Facturado a:', guestName),
                  const Divider(color: Colors.white10, height: 16),
                  _detailRow('Llegada (Check-in 15:00):', checkIn.isNotEmpty ? checkIn : 'Por definir'),
                  const Divider(color: Colors.white10, height: 16),
                  _detailRow('Salida (Check-out 13:00):', checkOut.isNotEmpty ? checkOut : 'Por definir'),
                  const Divider(color: Colors.white10, height: 16),
                  _detailRow('Estado de Reserva:', 'CONFIRMADA · PAGADA', color: const Color(0xFF2ECC71)),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Botón Regresar a Llave Digital
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.gold,
                  foregroundColor: Colors.black,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                icon: const Icon(Icons.key, size: 18),
                label: const Text('VER EN MI LLAVE DIGITAL', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1)),
                onPressed: () {
                  Navigator.of(context).popUntil((route) => route.isFirst);
                },
              ),
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _detailRow(String label, String value, {Color? color}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(color: Colors.white60, fontSize: 12)),
        Text(
          value,
          style: TextStyle(
            color: color ?? Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }
}
