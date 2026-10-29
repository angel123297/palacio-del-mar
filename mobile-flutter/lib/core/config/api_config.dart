import 'package:flutter/foundation.dart';

class ApiConfig {
  static const String _overrideUrl = String.fromEnvironment('API_URL');

  static String get baseUrl {
    if (_overrideUrl.isNotEmpty) {
      return _overrideUrl;
    }
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:5000/api';
    }
    return 'http://localhost:5000/api';
  }
}
