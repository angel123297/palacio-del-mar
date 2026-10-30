import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'core/config/api_config.dart';
import 'core/network/http_client.dart';
import 'dart:convert';

class ReservationSyncService {
  static String get baseUrl => ApiConfig.baseUrl;

  Future<void> saveGuestReservation(Map<String, dynamic> reservationData) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('guest_reservation', json.encode(reservationData));
  }

  Future<void> syncReservationOnAuth(String userToken, String userId) async {
    final prefs = await SharedPreferences.getInstance();
    final guestDataString = prefs.getString('guest_reservation');

    if (guestDataString != null) {
      try {
        final guestData = json.decode(guestDataString);
        final response = await AuthenticatedHttpClient.post('/bookings', {
          if (guestData is Map<String, dynamic>) ...guestData,
          'userId': userId,
        });

        if (response.statusCode == 200 || response.statusCode == 201) {
          await prefs.remove('guest_reservation');
        }
      } catch (e) {
        if (kDebugMode) {
          print('Error al sincronizar reserva offline: $e');
        }
      }
    }
  }
}
