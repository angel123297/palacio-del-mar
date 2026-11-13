import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:palacio_del_mar_mobile/screens/payment/receipt_screen.dart';

void main() {
  group('BookingReceiptScreen Truthful Data Tests', () {
    testWidgets('Muestra "Pendiente" y no genera PIN ni fechas falsas cuando no existen', (tester) async {
      final emptyBooking = <String, dynamic>{
        'id': 'booking-001',
        'suiteName': 'Suite Cartagena Bahía',
      };

      await tester.pumpWidget(
        MaterialApp(
          home: BookingReceiptScreen(
            booking: emptyBooking,
            amount: 450000,
            bookingCode: 'PDM-TEST1234',
          ),
        ),
      );

      // Verificación de PIN veraz
      expect(find.text('Pendiente'), findsOneWidget);
      expect(find.text('842917'), findsNothing);
      expect(find.text('182786'), findsNothing);

      // Verificación de fechas veraces sin fallbacks hardcodeados
      expect(find.textContaining('2026-11-07'), findsNothing);
      expect(find.textContaining('2026-11-09'), findsNothing);
      expect(find.text('Por definir'), findsNWidgets(2));
    });

    testWidgets('Muestra datos reales cuando el booking los contiene', (tester) async {
      final realBooking = <String, dynamic>{
        'id': 'booking-002',
        'suiteName': 'Suite Imperial',
        'smartLockPin': '987654',
        'checkIn': '2026-12-24',
        'checkOut': '2026-12-30',
        'guestName': 'Carlos Mendoza',
      };

      await tester.pumpWidget(
        MaterialApp(
          home: BookingReceiptScreen(
            booking: realBooking,
            amount: 1200000,
            bookingCode: 'PDM-REAL5678',
          ),
        ),
      );

      expect(find.text('987654'), findsOneWidget);
      expect(find.text('2026-12-24'), findsOneWidget);
      expect(find.text('2026-12-30'), findsOneWidget);
      expect(find.text('Carlos Mendoza'), findsOneWidget);
      expect(find.text('842917'), findsNothing);
    });
  });
}
