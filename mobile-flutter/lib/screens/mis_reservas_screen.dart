import 'package:flutter/material.dart';
import '../core/services/api_service.dart';

class MisReservasScreen extends StatefulWidget {
  const MisReservasScreen({Key? key}) : super(key: key);
  @override
  _MisReservasScreenState createState() => _MisReservasScreenState();
}

class _MisReservasScreenState extends State<MisReservasScreen> {
  final ApiService _apiService = ApiService();
  List<dynamic> _reservas = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _cargarReservas();
  }

  Future<void> _cargarReservas() async {
    try {
      final data = await ApiService.getMyBookings();
      setState(() {
        _reservas = data;
        _isLoading = false;
      });
    } catch (e) {
      setState(() { _isLoading = false; });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error al cargar historial: $e')),
      );
    }
  }

  Future<void> _cancelarReserva(String id) async {
    try {
      await ApiService.cancelBooking(id, reason: 'Cancelación por el usuario');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Reserva cancelada exitosamente')),
      );
      _cargarReservas();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Error al cancelar la reserva')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mis Reservas')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _reservas.isEmpty
              ? const Center(child: Text('No tienes reservas previas.'))
              : ListView.builder(
                  itemCount: _reservas.length,
                  itemBuilder: (context, index) {
                    final reserva = _reservas[index];
                    final bookingId = reserva['id'] ?? reserva['_id'];
                    final code = reserva['bookingCode'] ?? 'Sin código';
                    final status = reserva['status'] ?? 'pending';

                    return Card(
                      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      child: ListTile(
                        title: Text('Reserva: $code', style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text('Estado: $status'),
                        trailing: (status == 'pending' || status == 'confirmed')
                            ? IconButton(
                                icon: const Icon(Icons.cancel, color: Colors.redAccent),
                                onPressed: () => _cancelarReserva(bookingId),
                                tooltip: 'Cancelar reserva',
                              )
                            : null,
                      ),
                    );
                  },
                ),
    );
  }
}