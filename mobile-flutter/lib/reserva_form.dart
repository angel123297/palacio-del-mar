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
          body: jsonEncode({
            "email": cleanEmail,
            "password": password,
          }),
        );

        if (response.statusCode == 200 || response.statusCode == 201) {
          showDialog(
            context: context,
            builder: (context) => AlertDialog(
              title: Text(isLogin ? '¡Bienvenido!' : '¡Registro Exitoso!'),
              content: Text(isLogin 
                ? 'Sesión iniciada correctamente para $cleanEmail' 
                : 'Tu cuenta ha sido creada y sincronizada para la web y la app.'),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: Text('Aceptar'),
                ),
              ],
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error: ${response.body}'))
          );
        }
      } catch (e) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('No se pudo conectar con el servidor'))
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Color(0xFF121212),
      appBar: AppBar(
        title: Text(isLogin ? 'Palacio del Mar - Iniciar Sesión' : 'Palacio del Mar - Registro'),
        backgroundColor: Color(0xFF1E1E1E),
      ),
      body: Padding(
        padding: EdgeInsets.all(24.0),
        child: Center(
          child: SingleChildScrollView(
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    isLogin ? 'Acceso a tu cuenta' : 'Crear una cuenta nueva',
                    style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 20),
                  TextFormField(
                    controller: _emailController,
                    style: TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Correo Electrónico',
                      labelStyle: TextStyle(color: Colors.white70),
                      enabledBorder: OutlineInputBorder(borderSide: BorderSide(color: Colors.white54)),
                      focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: Color(0xFFD4AF37))),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) return 'Ingresa un correo';
                      if (!_emailRegex.hasMatch(value)) return 'Correo inválido';
                      return null;
                    },
                  ),
                  SizedBox(height: 15),
                  TextFormField(
                    controller: _passwordController,
                    obscureText: true,
                    style: TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Contraseña',
                      labelStyle: TextStyle(color: Colors.white70),
                      enabledBorder: OutlineInputBorder(borderSide: BorderSide(color: Colors.white54)),
                      focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: Color(0xFFD4AF37))),
                    ),
                    validator: (value) => (value == null || value.length < 6) ? 'Mínimo 6 caracteres' : null,
                  ),
                  SizedBox(height: 25),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Color(0xFFD4AF37)),
                    onPressed: _submitAuth,
                    child: Text(isLogin ? 'INICIAR SESIÓN' : 'REGISTRARSE', style: TextStyle(color: Colors.black)),
                  ),
                  TextButton(
                    onPressed: () => setState(() => isLogin = !isLogin),
                    child: Text(
                      isLogin ? '¿No tienes cuenta? Regístrate aquí' : '¿Ya tienes cuenta? Inicia sesión',
                      style: TextStyle(color: Color(0xFFD4AF37)),
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
