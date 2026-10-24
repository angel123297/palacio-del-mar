import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class ReservationSyncService {
  final String baseUrl = 'http://10.10.176.120:8080/api';

  Future<void> saveGuestReservation(Map<String, dynamic> reservationData) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('guest_reservation', json.encode(reservationData));
  }

  Future<void> syncReservationOnAuth(String userToken, String userId) async {
    final prefs = await SharedPreferences.getInstance();
    final guestDataString = prefs.getString('guest_reservation');

    if (guestDataString != null) {
      final guestData = json.decode(guestDataString);
      
      final response = await http.post(
        Uri.parse('$baseUrl/reservas/sincronizar'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $userToken',
        },
        body: json.encode({
          'userId': userId,
          'reservationDetails': guestData,
        }),
      );

      if (response.statusCode == 200) {
        await prefs.remove('guest_reservation');
      } else {
        throw Exception('Fallo al sincronizar la reserva con el servidor');
      }
    }
  }
}
