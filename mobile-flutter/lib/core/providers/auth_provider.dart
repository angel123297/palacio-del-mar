import 'package:flutter/material.dart';
import '../security/secure_storage.dart';
import '../services/api_service.dart';

class AuthProvider extends ChangeNotifier {
  bool _isAuthenticated = false;
  bool _isLoading = false;
  String? _currentUserEmail;

  bool get isAuthenticated => _isAuthenticated;
  bool get isLoading => _isLoading;
  String? get currentUserEmail => _currentUserEmail;

  Future<void> checkAuthStatus() async {
    final token = await SecureStorage.getToken();
    _isAuthenticated = token != null;
    notifyListeners();
  }

  Future<Map<String, dynamic>> login(String email, String password) async {
    _isLoading = true;
    notifyListeners();

    final res = await ApiService.login(email, password);
    if (res['success'] == true) {
      _isAuthenticated = true;
      _currentUserEmail = email.trim().toLowerCase();
    }
    
    _isLoading = false;
    notifyListeners();
    return res;
  }

  Future<Map<String, dynamic>> register(String name, String email, String password, {String? phone}) async {
    _isLoading = true;
    notifyListeners();

    final res = await ApiService.register(name, email, password, phone: phone);
    if (res['success'] == true) {
      if (res['data']?['token'] != null) {
        _isAuthenticated = true;
        _currentUserEmail = email.trim().toLowerCase();
      } else {
        _isAuthenticated = false;
      }
    }

    _isLoading = false;
    notifyListeners();
    return res;
  }

  Future<void> logout() async {
    await ApiService.logout();
    _isAuthenticated = false;
    _currentUserEmail = null;
    notifyListeners();
  }
}