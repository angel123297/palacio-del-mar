import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiService {
  final String baseUrl = 'http://10.10.176.120:8080/api';

  Future<List<dynamic>> obtenerDatos() async {
    try {
      final response = await http.get(Uri.parse('$baseUrl/habitaciones'));
      if (response.statusCode == 200) {
        return json.decode(response.body);
      } else {
        throw Exception('Error al conectar con Spring Boot');
      }
    } catch (e) {
      throw Exception('Fallo de red al conectar con Docker: $e');
    }
  }
}
