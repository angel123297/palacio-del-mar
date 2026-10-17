import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../core/services/api_service.dart';
import '../../core/services/storage_service.dart';
import '../auth/login_screen.dart';

class ReservationsScreen extends StatefulWidget {
  final String userEmail;
  const ReservationsScreen({super.key, this.userEmail = ''});

  @override
  State<ReservationsScreen> createState() => _ReservationsScreenState();
}

class _ReservationsScreenState extends State<ReservationsScreen> {
  List<dynamic> _reservations = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    setState(() => _isLoading = true);
    final data = await ApiService.getMyReservations();
    setState(() {
      _reservations = data;
      _isLoading = false;
    });
  }

  void _logout() async {
    await StorageService.deleteToken();
    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const LoginScreen()),
    );
  }

  void _copyPin(String pin) {
    Clipboard.setData(ClipboardData(text: pin));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('PIN copiado al portapapeles'),
        backgroundColor: Color(0xFFC5A059),
        duration: Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const goldColor = Color(0xFFC5A059);
    const darkBg = Color(0xFF0D0D0D);

    return Scaffold(
      backgroundColor: darkBg,
      appBar: AppBar(
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Palacio del Mar', style: TextStyle(fontFamily: 'serif', color: goldColor, fontSize: 20, letterSpacing: 1)),
            Text('MIS RESERVAS & LLAVE DIGITAL', style: TextStyle(color: Colors.white54, fontSize: 9, letterSpacing: 2)),
          ],
        ),
        backgroundColor: const Color(0xFF141414),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded, color: goldColor),
            onPressed: _fetchData,
            tooltip: 'Actualizar',
          ),
          IconButton(
            icon: const Icon(Icons.logout_rounded, color: goldColor),
            onPressed: _logout,
            tooltip: 'Cerrar Sesión',
          ),
        ],
      ),
      body: Center(
        child: Container(
          constraints: const BoxConstraints(maxWidth: 500),
          padding: const EdgeInsets.all(20.0),
          child: _isLoading
              ? const Center(child: CircularProgressIndicator(color: goldColor))
              : RefreshIndicator(
                  color: goldColor,
                  onRefresh: _fetchData,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      _buildReservationCard(
                        hotelName: 'Gran Suite Presidencial',
                        location: 'Centro Histórico · Cartagena',
                        code: 'RES-8921-PALACIO',
                        dates: '18 Oct - 20 Oct 2026',
                        pinCode: '8492',
                        status: 'CONFIRMADA',
                        goldColor: goldColor,
                      ),
                    ],
                  ),
                ),
        ),
      ),
    );
  }

  Widget _buildReservationCard({
    required String hotelName,
    required String location,
    required String code,
    required String dates,
    required String pinCode,
    required String status,
    required Color goldColor,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      padding: const EdgeInsets.all(24.0),
      decoration: BoxDecoration(
        color: const Color(0xFF161616),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: goldColor.withOpacity(0.3), width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.5),
            blurRadius: 20,
            offset: const Offset(0, 10),
          )
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      hotelName,
                      style: const TextStyle(
                        fontFamily: 'serif',
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(location, style: TextStyle(color: goldColor.withOpacity(0.9), fontSize: 12)),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: goldColor.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: goldColor.withOpacity(0.5)),
                ),
                child: Text(
                  status,
                  style: TextStyle(
                    color: goldColor,
                    fontWeight: FontWeight.bold,
                    fontSize: 10,
                    letterSpacing: 1,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              const Icon(Icons.calendar_today_outlined, size: 14, color: Colors.white54),
              const SizedBox(width: 6),
              Text(dates, style: const TextStyle(color: Colors.white54, fontSize: 13)),
            ],
          ),
          Divider(height: 32, color: Colors.white.withOpacity(0.1)),
          Text(
            'LLAVE DIGITAL DE ACCESO',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: goldColor,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Presenta este código en la cerradura inteligente',
            style: TextStyle(color: Colors.white38, fontSize: 11),
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(8),
            ),
            child: QrImageView(data: code, version: QrVersions.auto, size: 170.0),
          ),
          const SizedBox(height: 24),
          InkWell(
            onTap: () => _copyPin(pinCode),
            borderRadius: BorderRadius.circular(6),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 16),
              decoration: BoxDecoration(
                color: goldColor.withOpacity(0.1),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: goldColor.withOpacity(0.4)),
              ),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'PIN DE HABITACIÓN',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: goldColor,
                          letterSpacing: 2,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(Icons.copy_rounded, size: 14, color: goldColor),
                    ],
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    '8492',
                    style: TextStyle(
                      fontSize: 34,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 8,
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
