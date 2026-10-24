import 'package:flutter/material.dart';
import 'main.dart';

class AppRoutes {
  static const String home = '/';
  static const String login = '/login';
  static const String payments = '/pagos';

  static Map<String, WidgetBuilder> getRoutes() {
    return {
      home: (context) => const PalacioDelMarHome(),
    };
  }
}
