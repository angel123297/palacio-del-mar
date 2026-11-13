import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:palacio_del_mar_mobile/core/providers/auth_provider.dart';
import 'package:palacio_del_mar_mobile/screens/booking/booking_stepper_screen.dart';

void main() {
  group('BookingStepperScreen Pricing & Breakdown Tests', () {
    testWidgets('Calcula desglose con IVA 19% y Depósito 100k COP sin comisión de plataforma', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ChangeNotifierProvider(
              create: (_) => AuthProvider(),
              child: const BookingStepperScreen(
                suiteId: 'suite-deluxe-101',
                suiteName: 'Suite Deluxe Mar',
                pricePerNightCOP: 200000,
                token: 'mock-token',
                baseUrl: 'http://127.0.0.1:5000/api',
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Navegar a Paso 2 (Huéspedes)
      await tester.tap(find.widgetWithText(ElevatedButton, 'Siguiente').first, warnIfMissed: false);
      await tester.pumpAndSettle();

      // Verificar que el switch de mascota indica tarifa de $80.000 COP
      expect(find.text('Mascota / Pet-Friendly'), findsOneWidget);
      expect(find.text('+\$80.000 COP tarifa por estadía'), findsOneWidget);

      // Navegar a Paso 3 (Servicios)
      await tester.tap(find.widgetWithText(ElevatedButton, 'Siguiente').first, warnIfMissed: false);
      await tester.pumpAndSettle();

      // Navegar a Paso 4 (Resumen)
      await tester.tap(find.widgetWithText(ElevatedButton, 'Siguiente').first, warnIfMissed: false);
      await tester.pumpAndSettle();

      // Verificar el desglose de precios alineado con backend
      expect(find.textContaining('Alojamiento (2 noches):'), findsOneWidget);
      expect(find.textContaining('IVA Hospedaje (19%):'), findsOneWidget);
      expect(find.text('Depósito de Garantía (reembolsable):'), findsOneWidget);
      expect(find.text('\$100000 COP'), findsOneWidget);

      // La comisión de plataforma del 8% NO debe cobrarse al huésped
      expect(find.textContaining('Tarifa de servicio (8%)'), findsNothing);

      // Cálculo base: 2 noches * 200,000 = 400,000 COP
      // IVA (19%): 400,000 * 0.19 = 76,000 COP
      // Depósito: 100,000 COP
      // Total esperado: 400,000 + 76,000 + 100,000 = 576,000 COP
      expect(find.text('\$400000 COP'), findsOneWidget);
      expect(find.text('\$76000 COP'), findsOneWidget);
      expect(find.text('\$576000 COP'), findsOneWidget);
    });
  });
}
