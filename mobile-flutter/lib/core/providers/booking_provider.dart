import 'package:flutter/material.dart';

class BookingProvider extends ChangeNotifier {
  // Pedidos de gastronomía seleccionados por el huésped
  final Map<String, int> _foodQuantities = {};
  final Map<String, Map<String, dynamic>> _foodCatalog = {};

  Map<String, int> get foodQuantities => _foodQuantities;

  int getQuantity(String dishName) => _foodQuantities[dishName] ?? 0;

  void registerFoodItem(Map<String, dynamic> item) {
    final name = item['name']?.toString() ?? '';
    if (name.isNotEmpty) {
      _foodCatalog[name] = item;
    }
  }

  void addFoodItem(Map<String, dynamic> item) {
    final name = item['name']?.toString() ?? '';
    if (name.isEmpty) return;
    registerFoodItem(item);
    _foodQuantities[name] = (_foodQuantities[name] ?? 0) + 1;
    notifyListeners();
  }

  void removeFoodItem(String dishName) {
    if (!_foodQuantities.containsKey(dishName)) return;
    final current = _foodQuantities[dishName]!;
    if (current <= 1) {
      _foodQuantities.remove(dishName);
    } else {
      _foodQuantities[dishName] = current - 1;
    }
    notifyListeners();
  }

  void clearFoodOrders() {
    _foodQuantities.clear();
    notifyListeners();
  }

  int get totalFoodItems {
    return _foodQuantities.values.fold(0, (sum, count) => sum + count);
  }

  double get totalFoodPrice {
    double total = 0.0;
    _foodQuantities.forEach((name, qty) {
      final item = _foodCatalog[name];
      if (item != null && item['priceRaw'] != null) {
        final price = (item['priceRaw'] as num).toDouble();
        total += price * qty;
      }
    });
    return total;
  }

  List<Map<String, dynamic>> getFormattedFoodOrders() {
    final List<Map<String, dynamic>> orders = [];
    _foodQuantities.forEach((name, qty) {
      final item = _foodCatalog[name];
      final price = item?['priceRaw'] ?? 80000;
      for (int i = 0; i < qty; i++) {
        orders.add({
          'dishName': name,
          'price': price,
          'type': 'typical',
          'includesBreakfastCombo': false,
        });
      }
    });
    return orders;
  }
}
