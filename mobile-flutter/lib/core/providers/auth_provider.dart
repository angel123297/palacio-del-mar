import 'package:flutter/material.dart';
import '../security/secure_storage.dart';
import '../services/api_service.dart';

class AuthProvider extends ChangeNotifier {
  bool _isAuthenticated = false;
  bool _isLoading = false;
  String? _currentUserEmail;
  String? _currentUserName;
  String? _currentUserId;

  bool get isAuthenticated => _isAuthenticated;
  bool get isLoading => _isLoading;
  String? get currentUserEmail => _currentUserEmail;
  String? get currentUserName => _currentUserName;
  String? get currentUserId => _currentUserId;

  String get displayName {
    if (_currentUserName != null && _currentUserName!.trim().isNotEmpty) {
      return _currentUserName!.trim();
    }
    if (_currentUserEmail != null && _currentUserEmail!.isNotEmpty) {
      return _currentUserEmail!.split('@').first;
    }
    return 'Huésped';
  }

  Future<void> checkAuthStatus() async {
    final token = await SecureStorage.getToken();
    _isAuthenticated = token != null;
    if (_isAuthenticated) {
      await fetchUserProfile();
    }
    notifyListeners();
  }

  Future<void> fetchUserProfile() async {
    try {
      final user = await ApiService.getCurrentUser();
      if (user != null) {
        _currentUserId = user['id']?.toString() ?? user['_id']?.toString();
        _currentUserEmail = user['email']?.toString();
        final name = user['name']?.toString() ?? '';
        final lastName = user['lastName']?.toString() ?? '';
        _currentUserName = '$name $lastName'.trim();
        if (_currentUserName!.isEmpty) {
          _currentUserName = name.isNotEmpty ? name : null;
        }
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<Map<String, dynamic>> login(String email, String password) async {
    _isLoading = true;
    notifyListeners();

    final res = await ApiService.login(email, password);
    if (res['success'] == true) {
      _isAuthenticated = true;
      _currentUserEmail = email.trim().toLowerCase();
      final data = res['data'];
      final user = data?['user'] ?? data?['data']?['user'];
      if (user is Map) {
        _currentUserId = user['id']?.toString() ?? user['_id']?.toString();
        final name = user['name']?.toString() ?? '';
        final lastName = user['lastName']?.toString() ?? '';
        _currentUserName = '$name $lastName'.trim();
      }
      await fetchUserProfile();
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
        _currentUserName = name.trim();
        await fetchUserProfile();
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
    _currentUserName = null;
    _currentUserId = null;
    notifyListeners();
  }
}
