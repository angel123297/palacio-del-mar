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
