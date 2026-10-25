import 'dart:convert';
import 'package:http/http.dart' as http;
import '../security/secure_storage.dart';
import '../config/api_config.dart';

class AuthenticatedHttpClient {
  static Future<Map<String, String>> _getHeaders() async {
    final token = await SecureStorage.getToken();
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  static Future<http.Response> post(String endpoint, Map<String, dynamic> body) async {
    final headers = await _getHeaders();
    final url = Uri.parse('${ApiConfig.baseUrl}$endpoint');
    
    var response = await http.post(url, headers: headers, body: jsonEncode(body));
    if (response.statusCode == 401 && !endpoint.contains('/auth/')) {
      final refreshed = await _tryRefreshToken();
      if (refreshed) {
        final retryHeaders = await _getHeaders();
        response = await http.post(url, headers: retryHeaders, body: jsonEncode(body));
      } else {
        await SecureStorage.clearAuth();
      }
    }
    return response;
  }

  static Future<http.Response> get(String endpoint) async {
    final headers = await _getHeaders();
    final url = Uri.parse('${ApiConfig.baseUrl}$endpoint');
    
    var response = await http.get(url, headers: headers);
    if (response.statusCode == 401 && !endpoint.contains('/auth/')) {
      final refreshed = await _tryRefreshToken();
      if (refreshed) {
        final retryHeaders = await _getHeaders();
        response = await http.get(url, headers: retryHeaders);
      } else {
        await SecureStorage.clearAuth();
      }
    }
    return response;
  }

  static Future<bool> _tryRefreshToken() async {
    try {
      final refreshToken = await SecureStorage.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        return false;
      }
      final url = Uri.parse('${ApiConfig.baseUrl}/auth/refresh-token');
      final res = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refreshToken': refreshToken}),
      );
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        final newToken = data['token'] ?? data['data']?['token'];
        final newRefreshToken = data['refreshToken'] ?? data['data']?['refreshToken'];
        if (newToken != null) {
          await SecureStorage.saveToken(newToken);
        }
        if (newRefreshToken != null) {
          await SecureStorage.saveRefreshToken(newRefreshToken);
        }
        return true;
      }
    } catch (_) {}
    return false;
  }
}
