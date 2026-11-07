import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:async';
import 'dart:convert';
import '../../core/security/secure_storage.dart';
import '../../core/theme/app_theme.dart';

class EmailVerificationScreen extends StatefulWidget {
  final String email;
  final String baseUrl;
  final VoidCallback? onVerified;

  const EmailVerificationScreen({
    super.key,
    required this.email,
    this.baseUrl = 'https://unbroke-unsized-zandra.ngrok-free.dev/api',
    this.onVerified,
  });

  @override
  State<EmailVerificationScreen> createState() => _EmailVerificationScreenState();
}

class _EmailVerificationScreenState extends State<EmailVerificationScreen> {
  final TextEditingController _tokenController = TextEditingController();
  bool _isLoading = false;
  String? _errorMessage;

  // Temporizador para evitar spam de reenvío (60 segundos)
  int _resendCooldown = 0;
  Timer? _cooldownTimer;

  @override
  void dispose() {
    _tokenController.dispose();
    _cooldownTimer?.cancel();
    super.dispose();
  }

  void _startCooldown() {
    setState(() => _resendCooldown = 60);
    _cooldownTimer?.cancel();
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_resendCooldown > 0) {
        if (mounted) setState(() => _resendCooldown--);
      } else {
        timer.cancel();
      }
    });
  }

  Future<void> _verifyEmail() async {
    final token = _tokenController.text.trim();
    if (token.isEmpty) {
      setState(() => _errorMessage = 'Ingresa el código o token de verificación');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final response = await http.post(
        Uri.parse('${widget.baseUrl}/auth/verify-email'),
        headers: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true',
        },
        body: jsonEncode({'token': token}),
      );

      final data = jsonDecode(response.body);

      if (response.statusCode == 200 || response.statusCode == 201) {
        // Guardar token JWT para auto-login
        final jwtToken = data['token'] ?? (data['data'] != null ? data['data']['token'] : null);
        if (jwtToken != null && jwtToken.toString().isNotEmpty) {
          await SecureStorage.saveToken(jwtToken.toString());
        }

        if (!mounted) return;

        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('¡Correo verificado con éxito! Sesión iniciada.'),
            backgroundColor: Color(0xFF2ECC71),
          ),
        );

        widget.onVerified?.call();
        Navigator.pop(context, true);
      } else {
        setState(() => _errorMessage = data['message'] ?? 'Código inválido o expirado');
      }
    } catch (e) {
      setState(() => _errorMessage = 'Error de conexión con el servidor. Intenta de nuevo.');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _resendCode() async {
    if (_resendCooldown > 0) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final response = await http.post(
        Uri.parse('${widget.baseUrl}/auth/resend-verification'),
        headers: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true',
        },
        body: jsonEncode({'email': widget.email.trim().toLowerCase()}),
      );

      final data = jsonDecode(response.body);

      if (!mounted) return;

      if (response.statusCode == 200) {
        _startCooldown();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(data['message'] ?? 'Hemos enviado un nuevo correo de verificación.'),
            backgroundColor: AppTheme.gold,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(data['message'] ?? 'No se pudo reenviar el correo.'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Error de conexión al solicitar reenvío.'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D0D),
      appBar: AppBar(
        title: const Text(
          'Verificación de Cuenta',
          style: TextStyle(color: AppTheme.gold, fontSize: 16, fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 10),
            Center(
              child: Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: const Color(0xFFD4AF37).withValues(alpha: 0.15),
                  border: Border.all(color: const Color(0xFFD4AF37), width: 1.5),
                ),
                child: const Icon(Icons.mark_email_read_outlined, size: 40, color: Color(0xFFD4AF37)),
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'Confirma tu Dirección de Correo',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white, fontFamily: 'serif'),
            ),
            const SizedBox(height: 10),
            Text(
              'Te hemos enviado un correo de activación a:\n${widget.email}',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white70, fontSize: 13, height: 1.5),
            ),
            const SizedBox(height: 24),

            // Tarjeta informativa con las 2 formas de activación
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF161616),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.white12),
              ),
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '¿Cómo activar tu cuenta?',
                    style: TextStyle(color: AppTheme.gold, fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 8),
                  Text(
                    '1. Abre el mensaje en tu correo y pulsa el botón "Verificar mi Cuenta".\n'
                    '2. O copia el código de verificación del mensaje y pégalo abajo:',
                    style: TextStyle(color: Colors.white60, fontSize: 12, height: 1.4),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Campo para pegar el código / token
            TextField(
              controller: _tokenController,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontSize: 15, fontFamily: 'monospace'),
              decoration: InputDecoration(
                hintText: 'Pega el código o token aquí',
                hintStyle: const TextStyle(color: Colors.white24, fontSize: 13),
                filled: true,
                fillColor: Colors.black.withValues(alpha: 0.5),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Colors.white24),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Color(0xFFD4AF37)),
                ),
                errorText: _errorMessage,
              ),
            ),
            const SizedBox(height: 20),

            // Botón de Verificación
            ElevatedButton(
              onPressed: _isLoading ? null : _verifyEmail,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFD4AF37),
                foregroundColor: Colors.black,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
              ),
              child: _isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(color: Colors.black, strokeWidth: 2),
                    )
                  : const Text(
                      'VERIFICAR CUENTA',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, letterSpacing: 1),
                    ),
            ),
            const SizedBox(height: 16),

            // Botón de reenvío con temporizador
            TextButton(
              onPressed: (_isLoading || _resendCooldown > 0) ? null : _resendCode,
              child: Text(
                _resendCooldown > 0
                    ? 'Reenviar código en $_resendCooldown s'
                    : '¿No recibiste el correo? Reenviar enlace',
                style: TextStyle(
                  color: _resendCooldown > 0 ? Colors.white30 : const Color(0xFFD4AF37),
                  fontSize: 12,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
