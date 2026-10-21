import 'package:flutter/material.dart';
import '../suites/suites_screen.dart';
import '../gastronomy/gastronomy_screen.dart';
import '../auth/login_screen.dart';

class MainNavigation extends StatefulWidget {
  const MainNavigation({super.key});

  @override
  State<MainNavigation> createState() => _MainNavigationState();
}

class _MainNavigationState extends State<MainNavigation> {
  int _currentIndex = 0;

  final List<Widget> _screens = [
    const SuitesScreen(),
    const GastronomyScreen(),
    const LoginScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);

    return Scaffold(
      backgroundColor: const Color(0xFF0D0D0D),
      appBar: AppBar(
        backgroundColor: const Color(0xFF141414),
        elevation: 0,
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Palacio del Mar',
              style: TextStyle(fontFamily: 'serif', color: goldColor, fontSize: 20, fontWeight: FontWeight.bold, letterSpacing: 1),
            ),
            Text('HOTEL BOUTIQUE · CARTAGENA', style: TextStyle(color: Colors.white38, fontSize: 8, letterSpacing: 2)),
          ],
        ),
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) => setState(() => _currentIndex = index),
        backgroundColor: const Color(0xFF141414),
        selectedItemColor: goldColor,
        unselectedItemColor: Colors.white38,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11),
        unselectedLabelStyle: const TextStyle(fontSize: 11),
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.hotel_outlined), activeIcon: Icon(Icons.hotel), label: 'Suites'),
          BottomNavigationBarItem(icon: Icon(Icons.restaurant_outlined), activeIcon: Icon(Icons.restaurant), label: 'Gastronomía'),
          BottomNavigationBarItem(icon: Icon(Icons.key_outlined), activeIcon: Icon(Icons.key), label: 'Mi Llave Digital'),
        ],
      ),
    );
  }
}
