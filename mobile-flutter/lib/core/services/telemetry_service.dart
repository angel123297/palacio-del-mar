import 'package:flutter/foundation.dart';
import 'api_service.dart';

class TelemetryService {
  static void logAction(String event, Map<String, dynamic> payload) {
    final data = {
      'event': event,
      'platform': defaultTargetPlatform.toString(),
      'timestamp': DateTime.now().toIso8601String(),
      'details': payload,
    };
    ApiService.sendAuditLog(data).catchError((_) {});
    debugPrint('TELEMETRIA: $event -> $payload');
  }

  static void logError(Object error, StackTrace stack) {
    debugPrint('CRASH INTERCEPTADO: $error');
  }
}
