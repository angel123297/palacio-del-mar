import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/theme/app_theme.dart';
import 'core/providers/auth_provider.dart';
import 'core/providers/booking_provider.dart';
import 'core/services/telemetry_service.dart';
import 'features/home/home_view.dart';
import 'features/suites/suites_view.dart';
import 'features/gastronomy/gastronomy_view.dart';
import 'features/auth/digital_key_view.dart';

void main() {
  runZonedGuarded(() {
    WidgetsFlutterBinding.ensureInitialized();
    
    FlutterError.onError = (FlutterErrorDetails details) {
      FlutterError.presentError(details);
      TelemetryService.logError(details.exception, details.stack ?? StackTrace.empty);
    };

    runApp(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthProvider()..checkAuthStatus()),
          ChangeNotifierProvider(create: (_) => BookingProvider()),
        ],
        child: const PalacioDelMarApp(),
      ),
    );
  }, (error, stack) {
    TelemetryService.logError(error, stack);
  });
}

class PalacioDelMarApp extends StatelessWidget {
  const PalacioDelMarApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Palacio del Mar',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      home: const MainHomeScreen(),
    );
  }
}

class MainHomeScreen extends StatefulWidget {
  const MainHomeScreen({super.key});

  @override
  State<MainHomeScreen> createState() => _MainHomeScreenState();
}

class _MainHomeScreenState extends State<MainHomeScreen> {
  int _currentIndex = 0;

  void _navigateToTab(int index) {
    TelemetryService.logAction('cambio_pestana', {'destino': index});
    setState(() => _currentIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final totalItems = context.watch<BookingProvider>().totalItems;
    final List<Widget> pages = [
      HotelLandingView(onNavigateToSuites: () => _navigateToTab(1)),
      const SuitesCatalogView(),
      const GastronomySpaView(),
      const DigitalKeyView(),
    ];

    return Scaffold(
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        elevation: 0,
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Palacio del Mar', style: TextStyle(fontFamily: 'serif', color: AppTheme.gold, fontSize: 20, fontWeight: FontWeight.bold, letterSpacing: 1.2)),
            Text('HOTEL BOUTIQUE · CARTAGENA', style: TextStyle(color: Colors.white38, fontSize: 8, letterSpacing: 2)),
          ],
        ),
        actions: [
          Stack(
            alignment: Alignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.shopping_bag_outlined, color: AppTheme.gold),
                onPressed: () {
                  TelemetryService.logAction('ver_carrito', {'items': totalItems});
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Tienes $totalItems suite(s) en tu reserva', style: const TextStyle(color: Colors.black)), backgroundColor: AppTheme.gold));
                },
              ),
              if (totalItems > 0)
                Positioned(
                  right: 8, top: 8,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(color: Colors.redAccent, shape: BoxShape.circle),
                    child: Text('$totalItems', style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
                  ),
                )
            ],
          )
        ],
      ),
      body: pages[_currentIndex],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: _navigateToTab,
        backgroundColor: AppTheme.surface,
        selectedItemColor: AppTheme.gold,
        unselectedItemColor: Colors.white38,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 10),
        unselectedLabelStyle: const TextStyle(fontSize: 10),
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home_outlined), activeIcon: Icon(Icons.home), label: 'Inicio'),
          BottomNavigationBarItem(icon: Icon(Icons.hotel_outlined), activeIcon: Icon(Icons.hotel), label: 'Suites'),
          BottomNavigationBarItem(icon: Icon(Icons.restaurant_outlined), activeIcon: Icon(Icons.restaurant), label: 'Experiencias'),
          BottomNavigationBarItem(icon: Icon(Icons.key_outlined), activeIcon: Icon(Icons.key), label: 'Llave Digital'),
        ],
      ),
    );
  }
}
