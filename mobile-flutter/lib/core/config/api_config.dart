import 'package:flutter/foundation.dart';

class ApiConfig {
  static const String _overrideUrl = String.fromEnvironment('API_URL');
  static String? _runtimeBaseUrl;

  static void setBaseUrl(String url) {
    _runtimeBaseUrl = url.endsWith('/api') ? url : (url.endsWith('/') ? '${url}api' : '$url/api');
  }

  static String get baseUrl {
    if (_runtimeBaseUrl != null && _runtimeBaseUrl!.isNotEmpty) {
      return _runtimeBaseUrl!;
    }
    if (_overrideUrl.isNotEmpty) {
      return _overrideUrl.endsWith('/api') ? _overrideUrl : (_overrideUrl.endsWith('/') ? '${_overrideUrl}api' : '$_overrideUrl/api');
    }
    if (kIsWeb) {
      final host = Uri.base.host;
      final port = Uri.base.port;
      final scheme = Uri.base.scheme;
      if (host.isNotEmpty && host != 'localhost' && host != '127.0.0.1') {
        final portPart = (port == 80 || port == 443 || port == 0) ? '' : ':$port';
        return '$scheme://$host$portPart/api';
      }
      return 'http://localhost:5000/api';
    }
    if (defaultTargetPlatform == TargetPlatform.android) {
      // Por defecto en dispositivo físico usa la IP LAN del equipo servidor
      return 'http://192.168.80.15:5000/api';
    }
    return 'http://localhost:5000/api';
  }
}
