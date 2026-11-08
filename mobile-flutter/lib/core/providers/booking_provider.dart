import 'package:flutter/material.dart';

class BookingProvider extends ChangeNotifier {
  // Pedidos de gastronomía seleccionados por el huésped
  final Map<String, int> _foodQuantities = {};
  final Map<String, Map<String, dynamic>> _foodCatalog = {};

  // Experiencias de lujo seleccionadas para vincular a la reserva
  final Map<String, int> _experienceQuantities = {};
  final Map<String, Map<String, dynamic>> _experienceCatalog = {};

  Map<String, int> get foodQuantities => _foodQuantities;
  Map<String, Map<String, dynamic>> get foodCatalog => _foodCatalog;
  Map<String, int> get experienceQuantities => _experienceQuantities;
  Map<String, Map<String, dynamic>> get experienceCatalog => _experienceCatalog;

  int getQuantity(String dishName) => _foodQuantities[dishName] ?? 0;
  int getExperienceQuantity(String expId) => _experienceQuantities[expId] ?? 0;

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

  // --- MÉTODOS PARA EXPERIENCIAS DE LUJO ---
  void registerExperience(Map<String, dynamic> exp) {
    final id = (exp['id'] ?? exp['_id'] ?? exp['name'] ?? '').toString();
    if (id.isNotEmpty) {
      _experienceCatalog[id] = exp;
    }
  }

  void addExperience(Map<String, dynamic> exp) {
    final id = (exp['id'] ?? exp['_id'] ?? exp['name'] ?? '').toString();
    if (id.isEmpty) return;
    registerExperience(exp);
    _experienceQuantities[id] = (_experienceQuantities[id] ?? 0) + 1;
    notifyListeners();
  }

  void removeExperience(String expId) {
    if (!_experienceQuantities.containsKey(expId)) return;
    final current = _experienceQuantities[expId]!;
    if (current <= 1) {
      _experienceQuantities.remove(expId);
    } else {
      _experienceQuantities[expId] = current - 1;
    }
    notifyListeners();
  }

  void clearExperiences() {
    _experienceQuantities.clear();
    notifyListeners();
  }

  int get totalExperienceCount {
    return _experienceQuantities.values.fold(0, (sum, count) => sum + count);
  }

  double get totalExperiencePrice {
    double total = 0.0;
    _experienceQuantities.forEach((id, qty) {
      final exp = _experienceCatalog[id];
      if (exp != null) {
        final rawPrice = exp['price'];
        double price = 0.0;
        if (rawPrice is num) {
          price = rawPrice.toDouble();
        } else if (rawPrice is String) {
          final cleaned = rawPrice.replaceAll(RegExp(r'[^0-9]'), '');
          price = double.tryParse(cleaned) ?? 0.0;
        }
        total += price * qty;
      }
    });
    return total;
  }

  List<Map<String, dynamic>> getFormattedExperiences() {
    final List<Map<String, dynamic>> list = [];
    _experienceQuantities.forEach((id, qty) {
      final exp = _experienceCatalog[id];
      final rawPrice = exp?['price'] ?? 0;
      double price = 0.0;
      if (rawPrice is num) {
        price = rawPrice.toDouble();
      } else if (rawPrice is String) {
        final cleaned = rawPrice.replaceAll(RegExp(r'[^0-9]'), '');
        price = double.tryParse(cleaned) ?? 0.0;
      }

      list.add({
        'experienceId': id,
        'name': exp?['name'] ?? 'Experiencia de Lujo',
        'price': price,
        'quantity': qty,
      });
    });
    return list;
  }
}
