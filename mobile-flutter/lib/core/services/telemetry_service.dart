import 'package:flutter/foundation.dart';

class TelemetryService {
  static void logAction(String event, Map<String, dynamic> payload) {
    debugPrint('TELEMETRIA: $event -> $payload');
  }

  static void logError(Object error, StackTrace stack) {
    debugPrint('CRASH INTERCEPTADO: $error');
  }
}
