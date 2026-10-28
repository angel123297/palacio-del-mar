class ApiConfig {
  static const String baseUrl = String.fromEnvironment(
    'API_URL',
    defaultValue: 'http://localhost:5000/api',
  );
  static const String emulatorUrl = 'http://10.0.2.2:5000/api';
}
