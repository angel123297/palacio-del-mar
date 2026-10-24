import 'dart:convert';
import '../network/http_client.dart';
import '../security/secure_storage.dart';

class ApiService {
  static Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final response = await AuthenticatedHttpClient.post('/auth/login', {
        'email': email.trim().toLowerCase(),
        'password': password,
      });

      Map<String, dynamic> responseData = {};
      try {
        responseData = jsonDecode(response.body);
      } catch (_) {}

      if (response.statusCode == 200) {
        final token = responseData['token'] ?? (responseData['data'] != null ? responseData['data']['token'] : null);
        if (token != null) {
          await SecureStorage.saveToken(token);
        }
        return {'success': true, 'data': responseData};
      } else if (response.statusCode == 401) {
        return {'success': false, 'statusCode': 401, 'message': responseData['message'] ?? 'Credenciales inválidas'};
      } else {
        return {'success': false, 'statusCode': response.statusCode, 'message': responseData['message'] ?? 'Error en inicio de sesión (${response.statusCode})'};
      }
    } catch (e) {
      return {'success': false, 'error': 'Error de conexión', 'message': 'No se pudo conectar con el servidor'};
    }
  }

  static Future<Map<String, dynamic>> register(String name, String email, String password, {String? phone}) async {
    try {
      final names = name.trim().split(' ');
      final firstName = names.isNotEmpty ? names.first : name.trim();
      final lastName = names.length > 1 ? names.sublist(1).join(' ') : '';

      final response = await AuthenticatedHttpClient.post('/auth/register', {
        'name': firstName,
        'lastName': lastName,
        'email': email.trim().toLowerCase(),
        'password': password,
        'phone': phone ?? '',
      });

      Map<String, dynamic> responseData = {};
      try {
        responseData = jsonDecode(response.body);
      } catch (_) {}

      if (response.statusCode == 200 || response.statusCode == 201) {
        final token = responseData['token'] ?? (responseData['data'] != null ? responseData['data']['token'] : null);
        if (token != null) {
          await SecureStorage.saveToken(token);
        }
        return {
          'success': true,
          'statusCode': response.statusCode,
          'data': responseData,
          'message': responseData['message'] ?? 'Registro exitoso'
        };
      } else if (response.statusCode == 409) {
        return {
          'success': false,
          'statusCode': 409,
          'message': responseData['message'] ?? 'El correo electrónico ya se encuentra registrado'
        };
      } else {
        return {
          'success': false,
          'statusCode': response.statusCode,
          'message': responseData['message'] ?? 'Error al procesar el registro (${response.statusCode})'
        };
      }
    } catch (e) {
      return {'success': false, 'error': 'Error de conexión', 'message': 'No se pudo conectar con el servidor'};
    }
  }

  static Future<List<dynamic>> getMyReservations() async {
    try {
      final response = await AuthenticatedHttpClient.get('/bookings');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data is List) return data;
        if (data is Map && data['data'] is List) return data['data'];
      }
      return [];
    } catch (_) {
      return [];
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
    } catch (_) {}
  }

  static Future<void> logout() async {
    await SecureStorage.deleteToken();
  }
}