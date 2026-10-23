import 'dart:convert';
import '../network/http_client.dart';
import '../security/secure_storage.dart';

class ApiService {
  static Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final response = await AuthenticatedHttpClient.post('/auth/login', {
        'email': email,
        'password': password,
      });
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data['token'] != null) {
          await SecureStorage.saveToken(data['token']);
        }
        return {'success': true, 'data': data};
      }
      return {'success': false, 'error': 'Credenciales invalidas'};
    } catch (e) {
      return {'success': false, 'error': 'Error de conexion'};
    }
  }

  static Future<bool> createBooking(Map<String, dynamic> bookingData) async {
    try {
      final response = await AuthenticatedHttpClient.post('/bookings', bookingData);
      return response.statusCode == 200 || response.statusCode == 201;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> sendContactMessage(Map<String, dynamic> messageData) async {
    try {
      final response = await AuthenticatedHttpClient.post('/contact', messageData);
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }

  static Future<void> sendAuditLog(Map<String, dynamic> auditData) async {
    try {
      await AuthenticatedHttpClient.post('/audit/log', auditData);
    } catch (e) {}
  }

  static Future<void> logout() async {
    await SecureStorage.deleteToken();
  }
}
