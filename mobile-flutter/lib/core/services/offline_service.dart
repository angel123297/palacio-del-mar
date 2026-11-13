import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

class OfflineService {
  static Future<void> saveOfflineKey(Map<String, dynamic> keyData) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('offline_key', jsonEncode(keyData));
  }

  static Future<Map<String, dynamic>?> getOfflineKey() async {
    final prefs = await SharedPreferences.getInstance();
    final data = prefs.getString('offline_key');
    if (data != null) return jsonDecode(data);
    return null;
  }
}
