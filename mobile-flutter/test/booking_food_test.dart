import 'package:flutter_test/flutter_test.dart';
import 'package:palacio_del_mar_mobile/core/providers/booking_provider.dart';

void main() {
  group('BookingProvider Food Ordering Tests', () {
    test('Estado inicial no tiene comidas y total es 0', () {
      final provider = BookingProvider();
      expect(provider.totalFoodItems, 0);
      expect(provider.totalFoodPrice, 0.0);
      expect(provider.foodQuantities.isEmpty, isTrue);
      expect(provider.getFormattedFoodOrders().isEmpty, isTrue);
    });

    test('addFoodItem incrementa cantidad y recalcula total en COP', () {
      final provider = BookingProvider();
      final ceviche = {
        'name': 'Ceviche Mixto Caribeño',
        'priceRaw': 45000,
      };

      provider.addFoodItem(ceviche);
      expect(provider.getQuantity('Ceviche Mixto Caribeño'), 1);
      expect(provider.totalFoodItems, 1);
      expect(provider.totalFoodPrice, 45000.0);

      // Agregar segunda porción
      provider.addFoodItem(ceviche);
      expect(provider.getQuantity('Ceviche Mixto Caribeño'), 2);
      expect(provider.totalFoodItems, 2);
      expect(provider.totalFoodPrice, 90000.0);
    });

    test('removeFoodItem decrementa y elimina al llegar a 0', () {
      final provider = BookingProvider();
      final langosta = {
        'name': 'Langosta al Termidor',
        'priceRaw': 120000,
      };

      provider.addFoodItem(langosta);
      provider.addFoodItem(langosta);
      expect(provider.getQuantity('Langosta al Termidor'), 2);

      provider.removeFoodItem('Langosta al Termidor');
      expect(provider.getQuantity('Langosta al Termidor'), 1);
      expect(provider.totalFoodPrice, 120000.0);

      provider.removeFoodItem('Langosta al Termidor');
      expect(provider.getQuantity('Langosta al Termidor'), 0);
      expect(provider.totalFoodPrice, 0.0);
      expect(provider.totalFoodItems, 0);
    });

    test('getFormattedFoodOrders formatea lista compatible con el backend', () {
      final provider = BookingProvider();
      provider.addFoodItem({
        'name': 'Ceviche Mixto',
        'priceRaw': 45000,
      });
      provider.addFoodItem({
        'name': 'Ceviche Mixto',
        'priceRaw': 45000,
      });

      final orders = provider.getFormattedFoodOrders();
      expect(orders.length, 2);
      expect(orders[0]['dishName'], 'Ceviche Mixto');
      expect(orders[0]['price'], 45000);
      expect(orders[1]['dishName'], 'Ceviche Mixto');
      expect(orders[1]['price'], 45000);
    });

    test('clearFoodOrders reinicia todo el pedido', () {
      final provider = BookingProvider();
      provider.addFoodItem({'name': 'Test', 'priceRaw': 10000});
      expect(provider.totalFoodItems, 1);

      provider.clearFoodOrders();
      expect(provider.totalFoodItems, 0);
      expect(provider.totalFoodPrice, 0.0);
    });
  });
}
