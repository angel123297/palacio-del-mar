import 'package:flutter/material.dart';
import '../security/secure_storage.dart';
import '../services/api_service.dart';

class AuthProvider extends ChangeNotifier {
  bool _isAuthenticated = false;
  bool _isLoading = false;

  bool get isAuthenticated => _isAuthenticated;
  bool get isLoading => _isLoading;

  Future<void> checkAuthStatus() async {
    final token = await SecureStorage.getToken();
    _isAuthenticated = token != null;
    notifyListeners();
  }

  Future<bool> login(String email, String password) async {
    _isLoading = true;
    notifyListeners();

    final res = await ApiService.login(email, password);
    if (res['success'] == true) {
      _isAuthenticated = true;
    }
    
    _isLoading = false;
    notifyListeners();
    return _isAuthenticated;
  }

  Future<void> logout() async {
    await ApiService.logout();
    _isAuthenticated = false;
    notifyListeners();
  }
}
