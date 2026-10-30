import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../security/secure_storage.dart';
import '../config/api_config.dart';

class AuthenticatedHttpClient {
  static const Duration _timeout = Duration(seconds: 15);
  static Completer<bool>? _refreshCompleter;

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
      final refreshed = await _tryRefreshToken();
      if (refreshed) {
        final retryHeaders = await _getHeaders();
        try {
          response = await http
              .post(url, headers: retryHeaders, body: jsonEncode(body))
              .timeout(_timeout);
        } catch (_) {}
      } else {
        await SecureStorage.clearAuth();
      }
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
      final refreshed = await _tryRefreshToken();
      if (refreshed) {
        final retryHeaders = await _getHeaders();
        try {
          response = await http.get(url, headers: retryHeaders).timeout(_timeout);
        } catch (_) {}
      } else {
        await SecureStorage.clearAuth();
      }
    }
    return response;
  }

  static Future<bool> _tryRefreshToken() async {
    if (_refreshCompleter != null) {
      return _refreshCompleter!.future;
    }

    _refreshCompleter = Completer<bool>();

    try {
      final refreshToken = await SecureStorage.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        _refreshCompleter!.complete(false);
        _refreshCompleter = null;
        return false;
      }
      final url = Uri.parse('${ApiConfig.baseUrl}/auth/refresh-token');
      final res = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refreshToken': refreshToken}),
      ).timeout(_timeout);

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
        _refreshCompleter!.complete(true);
        _refreshCompleter = null;
        return true;
      }
    } catch (_) {}

    _refreshCompleter?.complete(false);
    _refreshCompleter = null;
    return false;
  }
}
