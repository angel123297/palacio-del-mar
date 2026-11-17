import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:palacio_del_mar_mobile/core/providers/auth_provider.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (MethodCall methodCall) async {
        return null;
      },
    );
  });

  group('AuthProvider Tests', () {
    test('restaura la sesión web existente al abrir la aplicación', () async {
      final auth = AuthProvider(
        useBrowserSession: true,
        loadSessionUser: () async => {'id': '1', 'email': 'test@example.com', 'name': 'Ana', 'emailVerified': true},
      );
      await auth.checkAuthStatus();
      expect(auth.isAuthenticated, isTrue);
      expect(auth.currentUserEmail, 'test@example.com');
    });

    test('Estado inicial no autenticado', () {
      final auth = AuthProvider();
      expect(auth.isAuthenticated, isFalse);
      expect(auth.isLoading, isFalse);
      expect(auth.currentUserEmail, isNull);
    });

    test('logout limpia el estado reactivo', () async {
      final auth = AuthProvider();
      await auth.logout();
      expect(auth.isAuthenticated, isFalse);
      expect(auth.currentUserEmail, isNull);
    });
  });
}
