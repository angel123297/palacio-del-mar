import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../security/secure_storage.dart';
import '../config/api_config.dart';

class AuthenticatedHttpClient {
  static const Duration _timeout = Duration(seconds: 15);

  static Future<Map<String, String>> _getHeaders() async {
    final token = await SecureStorage.getToken();
    return {
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  static Future<http.Response> post(String endpoint, Map<String, dynamic> body) async {
    final headers = await _getHeaders();
    final url = Uri.parse('${ApiConfig.baseUrl}$endpoint');

    http.Response response;
    try {
      response = await http
          .post(url, headers: headers, body: jsonEncode(body))
          .timeout(_timeout);
    } on TimeoutException {
      return http.Response(
        jsonEncode({'success': false, 'message': 'Tiempo de espera agotado al conectar con el servidor'}),
        408,
        headers: {'Content-Type': 'application/json'},
      );
    }

    if (response.statusCode == 401 && !endpoint.contains('/auth/')) {
      await SecureStorage.clearAuth();
    }
    return response;
  }

  static Future<http.Response> get(String endpoint) async {
    final headers = await _getHeaders();
    final url = Uri.parse('${ApiConfig.baseUrl}$endpoint');

    http.Response response;
    try {
      response = await http.get(url, headers: headers).timeout(_timeout);
    } on TimeoutException {
      return http.Response(
        jsonEncode({'success': false, 'message': 'Tiempo de espera agotado al conectar con el servidor'}),
        408,
        headers: {'Content-Type': 'application/json'},
      );
    }

    if (response.statusCode == 401 && !endpoint.contains('/auth/')) {
      await SecureStorage.clearAuth();
    }
    return response;
  }
}
