import 'dart:async';
import 'dart:convert';
import 'dart:io';
import '../config/api_config.dart';
import '../security/secure_storage.dart';

class BookingStreamClient {
  static final BookingStreamClient _instance = BookingStreamClient._internal();
  factory BookingStreamClient() => _instance;
  BookingStreamClient._internal();

  final StreamController<Map<String, dynamic>> _bookingUpdatesController =
      StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get bookingUpdates => _bookingUpdatesController.stream;

  HttpClient? _client;
  bool _isConnected = false;
  Timer? _reconnectTimer;

  Future<void> connect() async {
    if (_isConnected) return;

    final token = await SecureStorage.getToken();
    if (token == null || token.isEmpty) return;

    try {
      _client = HttpClient();
      final uri = Uri.parse('${ApiConfig.baseUrl}/bookings/stream?token=${Uri.encodeComponent(token)}');
      final request = await _client!.getUrl(uri);
      request.headers.set(HttpHeaders.acceptHeader, 'text/event-stream');
      request.headers.set(HttpHeaders.cacheControlHeader, 'no-cache');

      final response = await request.close();
      if (response.statusCode != 200) {
        _scheduleReconnect();
        return;
      }

      _isConnected = true;

      response
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(
        (line) {
          if (line.startsWith('data:')) {
            final rawJson = line.substring(5).trim();
            if (rawJson.isNotEmpty) {
              try {
                final decoded = jsonDecode(rawJson);
                if (decoded is Map<String, dynamic>) {
                  _bookingUpdatesController.add(decoded);
                }
              } catch (_) {}
            }
          }
        },
        onError: (err) {
          _handleDisconnect();
        },
        onDone: () {
          _handleDisconnect();
        },
        cancelOnError: true,
      );
    } catch (e) {
      _handleDisconnect();
    }
  }

  void _handleDisconnect() {
    _isConnected = false;
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 5), () {
      connect();
    });
  }

  void disconnect() {
    _reconnectTimer?.cancel();
    _client?.close(force: true);
    _client = null;
    _isConnected = false;
  }
}
