import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class ReservaFormScreen extends StatefulWidget {
  @override
  _ReservaFormScreenState createState() => _ReservaFormScreenState();
}

class _ReservaFormScreenState extends State<ReservaFormScreen> {
  bool isLogin = true;
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  final RegExp _emailRegex = RegExp(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$');
  final String apiUrl = "https://tu-backend-produccion.com/api/auth";

  Future<void> _submitAuth() async {
    if (_formKey.currentState!.validate()) {
      String cleanEmail = _emailController.text.trim().toLowerCase();
      String password = _passwordController.text;
      String endpoint = isLogin ? "$apiUrl/login" : "$apiUrl/register";

      try {
        final response = await http.post(
          Uri.parse(endpoint),
          headers: {"Content-Type": "application/json"},
          body: jsonEncode({"email": cleanEmail, "password": password}),
        );

        if (response.statusCode == 200 || response.statusCode == 201) {
          showDialog(
            context: context,
            builder: (context) => AlertDialog(
              title: Text(isLogin ? 'Bienvenido' : 'Registro Exitoso'),
              content: Text(isLogin ? 'Sesion iniciada para $cleanEmail' : 'Cuenta creada correctamente.'),
              actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('Aceptar'))],
            ),
          );
        } else if (response.statusCode == 409) {
          showDialog(
            context: context,
            builder: (context) => AlertDialog(
              title: Text('Correo ya registrado'),
              content: Text('Este correo ya cuenta con una cuenta asociada. Intenta iniciar sesion.'),
              actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('Aceptar'))],
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error en el servidor')));
        }
      } catch (e) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('No se pudo conectar con el servidor')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        child: Padding(
          padding: EdgeInsets.all(16.0),
          child: Container(
            width: 400,
            padding: EdgeInsets.all(24.0),
            decoration: BoxDecoration(
              color: Color(0xFF1E1E1E),
              border: Border.all(color: Color(0xFFD4AF37), width: 1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Palacio del Mar',
                    style: TextStyle(color: Color(0xFFD4AF37), fontSize: 22, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 8),
                  Text(
                    isLogin ? 'INICIAR SESION' : 'REGISTRO DE USUARIO',
                    style: TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                  SizedBox(height: 24),
                  TextFormField(
                    controller: _emailController,
                    style: TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Correo Electronico',
                      labelStyle: TextStyle(color: Colors.white70),
                      enabledBorder: OutlineInputBorder(borderSide: BorderSide(color: Colors.white54)),
                      focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: Color(0xFFD4AF37))),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) return 'Ingresa un correo';
                      if (!_emailRegex.hasMatch(value)) return 'Correo invalido';
                      return null;
                    },
                  ),
                  SizedBox(height: 16),
                  TextFormField(
                    controller: _passwordController,
                    obscureText: true,
                    style: TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Contrasena',
                      labelStyle: TextStyle(color: Colors.white70),
                      enabledBorder: OutlineInputBorder(borderSide: BorderSide(color: Colors.white54)),
                      focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: Color(0xFFD4AF37))),
                    ),
                    validator: (value) => (value == null || value.length < 6) ? 'Minimo 6 caracteres' : null,
                  ),
                  SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(backgroundColor: Color(0xFFD4AF37)),
                      onPressed: _submitAuth,
                      child: Text(
                        isLogin ? 'INICIAR SESION' : 'REGISTRARSE',
                        style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                  SizedBox(height: 16),
                  Divider(color: Colors.white24),
                  SizedBox(height: 8),
                  TextButton(
                    onPressed: () {
                      setState(() {
                        isLogin = !isLogin;
                      });
                    },
                    child: Text(
                      isLogin ? 'No tienes cuenta? Registrate aqui' : 'Ya tienes cuenta? Inicia sesion',
                      style: TextStyle(color: Color(0xFFD4AF37), fontSize: 14, fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
