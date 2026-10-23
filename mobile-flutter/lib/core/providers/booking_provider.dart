import 'package:flutter/material.dart';

class BookingProvider extends ChangeNotifier {
  final List<Map<String, dynamic>> _cart = [];

  List<Map<String, dynamic>> get cart => _cart;
  int get totalItems => _cart.length;

  void addSuiteToCart(Map<String, dynamic> suite) {
    _cart.add(suite);
    notifyListeners();
  }

  void removeSuite(int index) {
    _cart.removeAt(index);
    notifyListeners();
  }

  void clearCart() {
    _cart.clear();
    notifyListeners();
  }
}
