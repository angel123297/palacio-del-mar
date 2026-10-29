import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:palacio_del_mar_mobile/core/providers/auth_provider.dart';
import 'package:palacio_del_mar_mobile/features/auth/digital_key_view.dart';

void main() {
  testWidgets('DigitalKeyView renders authentication toggle form', (WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ChangeNotifierProvider(
            create: (_) => AuthProvider(),
            child: const DigitalKeyView(),
          ),
        ),
      ),
    );

    expect(find.text('Palacio del Mar'), findsOneWidget);
    expect(find.text('LLAVE DIGITAL & ACCESO A SUITES'), findsOneWidget);
    expect(find.text('INICIAR SESIÓN'), findsOneWidget);
    expect(find.text('¿NO TIENES CUENTA? REGÍSTRATE AQUÍ'), findsOneWidget);

    // Alternar a modo registro
    await tester.tap(find.text('¿NO TIENES CUENTA? REGÍSTRATE AQUÍ'));
    await tester.pump();

    expect(find.text('CREAR CUENTA DE HUÉSPED'), findsOneWidget);
    expect(find.text('REGISTRARSE'), findsOneWidget);
    expect(find.text('¿YA TIENES CUENTA? INICIA SESIÓN'), findsOneWidget);
  });
}