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
    
    final response = await http.post(url, headers: headers, body: jsonEncode(body));
    _handleUnauthorized(response);
    return response;
  }

  static Future<http.Response> get(String endpoint) async {
    final headers = await _getHeaders();
    final url = Uri.parse('${ApiConfig.baseUrl}$endpoint');
    
    final response = await http.get(url, headers: headers);
    _handleUnauthorized(response);
    return response;
  }

  static void _handleUnauthorized(http.Response response) {
    if (response.statusCode == 401 || response.statusCode == 403) {
      SecureStorage.deleteToken();
    }
  }
}
