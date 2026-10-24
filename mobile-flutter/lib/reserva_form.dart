import 'package:flutter/material.dart';

class ReservaFormScreen extends StatefulWidget {
  @override
  _ReservaFormScreenState createState() => _ReservaFormScreenState();
}

class _ReservaFormScreenState extends State<ReservaFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _guestNameController = TextEditingController();
  final TextEditingController _hotelDetailsController = TextEditingController();
  final TextEditingController _amountController = TextEditingController();

  final RegExp _emailRegex = RegExp(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$');

  void _submitForm() {
    if (_formKey.currentState!.validate()) {
      // Normalización automática: quita espacios y convierte a minúsculas
      String cleanEmail = _emailController.text.trim().toLowerCase();

      final payload = {
        "email": cleanEmail,
        "guestName": _guestNameController.text,
        "hotelDetails": _hotelDetailsController.text,
        "amount": _amountController.text,
      };

      print("Enviando datos limpios desde la app móvil: $payload");

      // Alerta visual de éxito para el usuario
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: Text('¡Reserva Exitosa!'),
          text: Text('Hemos procesado tu reserva y enviado tu comprobante a $cleanEmail'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text('Aceptar'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Reserva - Palacio del Mar')),
      body: Padding(
        padding: EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            children: [
              TextFormField(
                controller: _emailController,
                decoration: InputDecoration(labelText: 'Correo electrónico'),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Por favor ingresa un correo';
                  }
                  if (!_emailRegex.hasMatch(value)) {
                    return 'Ingresa un correo electrónico válido';
                  }
                  return null;
                },
              ),
              TextFormField(
                controller: _guestNameController,
                decoration: InputDecoration(labelText: 'Nombre del huésped'),
                validator: (value) => value!.isEmpty ? 'Campo requerido' : null,
              ),
              TextFormField(
                controller: _hotelDetailsController,
                decoration: InputDecoration(labelText: 'Detalles del hotel'),
                validator: (value) => value!.isEmpty ? 'Campo requerido' : null,
              ),
              TextFormField(
                controller: _amountController,
                decoration: InputDecoration(labelText: 'Monto total'),
                keyboardType: TextInputType.number,
                validator: (value) => value!.isEmpty ? 'Campo requerido' : null,
              ),
              SizedBox(height: 20),
              ElevatedButton(
                onPressed: _submitForm,
                child: Text('Completar Reserva'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
