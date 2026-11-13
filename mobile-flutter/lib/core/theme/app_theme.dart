import 'package:flutter/material.dart';

class AppTheme {
  static const gold = Color(0xFFC5A059);
  static const darkBg = Color(0xFF0B0C10);
  static const cardBg = Color(0xFF141414);
  static const surface = Color(0xFF121212);

  static ThemeData get darkTheme {
    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: darkBg,
      primaryColor: gold,
      colorScheme: const ColorScheme.dark(
        primary: gold,
        surface: cardBg,
      ),
      useMaterial3: true,
    );
  }
}
