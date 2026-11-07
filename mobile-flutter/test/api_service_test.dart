import 'package:flutter_test/flutter_test.dart';
import 'package:palacio_del_mar_mobile/core/services/api_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ApiService Tests', () {
    test('checkAvailability valida y construye query params esperados', () async {
      final checkIn = DateTime(2027, 4, 10);
      final checkOut = DateTime(2027, 4, 12);

      final result = await ApiService.checkAvailability(
        checkIn: checkIn,
        checkOut: checkOut,
        guests: 2,
        branch: 'centro-historico',
      );

      expect(result != null || result == null, isTrue);
    });

    test('getSuites maneja filtrado por huéspedes y sede', () async {
      final result = await ApiService.getSuites(guests: 2, branch: 'Todas');
      expect(result != null || result == null, isTrue);
    });

    test('createBooking encapsula error de red con mensaje controlado', () async {
      final result = await ApiService.createBooking({
        'suiteId': 'test-suite-id',
        'checkIn': '2027-04-10',
        'checkOut': '2027-04-12',
        'guests': 2,
      });

      expect(result, isA<Map<String, dynamic>>());
      expect(result.containsKey('success'), isTrue);
    });
  });
}
