import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../core/theme/app_theme.dart';
import '../../core/providers/auth_provider.dart';

class DigitalKeyView extends StatefulWidget {
  const DigitalKeyView({super.key});

  @override
  State<DigitalKeyView> createState() => _DigitalKeyViewState();
}

class _DigitalKeyViewState extends State<DigitalKeyView> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  
  bool _isValidEmail(String email) {
    return RegExp(r"^[a-zA-Z0-9.a-zA-Z0-9.!#$%&'*+-/=?^_`{|}~]+@[a-zA-Z0-9]+\.[a-zA-Z]+").hasMatch(email);
  }

  void _login() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();

    if (email.isEmpty || password.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Ingresa correo y contraseña')));
      return;
    }

    if (!_isValidEmail(email)) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Formato de correo inválido')));
      return;
    }

    final success = await context.read<AuthProvider>().login(email, password);
    
    if (!mounted) return;
    if (!success) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Credenciales incorrectas o error de red')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();

    if (authProvider.isAuthenticated) {
      return Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Container(
            constraints: const BoxConstraints(maxWidth: 440),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppTheme.cardBg,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.gold.withOpacity(0.4)),
            ),
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('Gran Suite Presidencial', style: TextStyle(fontFamily: 'serif', color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
                    IconButton(
                      icon: const Icon(Icons.logout, color: Colors.white54, size: 20),
                      onPressed: () => context.read<AuthProvider>().logout(),
                    )
                  ],
                ),
                const Text('Centro Histórico · Cartagena', style: TextStyle(color: AppTheme.gold, fontSize: 12)),
                const Divider(height: 30, color: Colors.white10),
                const Text('LLAVE DIGITAL DE ACCESO', style: TextStyle(color: AppTheme.gold, fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 2)),
                const SizedBox(height: 4),
                const Text('Presenta este código en la cerradura inteligente', style: TextStyle(color: Colors.white38, fontSize: 10)),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
                  child: QrImageView(data: 'RES-8921-PALACIO', version: QrVersions.auto, size: 160),
                ),
                const SizedBox(height: 20),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: AppTheme.gold.withOpacity(0.1), borderRadius: BorderRadius.circular(6), border: Border.all(color: AppTheme.gold.withOpacity(0.3))),
                  child: const Column(
                    children: [
                      Text('PIN DE HABITACIÓN', style: TextStyle(color: AppTheme.gold, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 2)),
                      SizedBox(height: 4),
                      Text('8492', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold, letterSpacing: 6)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Container(
          constraints: const BoxConstraints(maxWidth: 400),
          padding: const EdgeInsets.all(28),
          decoration: BoxDecoration(
            color: AppTheme.cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppTheme.gold.withOpacity(0.3)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Palacio del Mar', style: TextStyle(fontFamily: 'serif', color: AppTheme.gold, fontSize: 24, fontWeight: FontWeight.bold)),
              const Text('LLAVE DIGITAL & ACCESO A SUITES', style: TextStyle(color: Colors.white54, fontSize: 10, letterSpacing: 1.5)),
              const SizedBox(height: 24),
              TextField(
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Correo Electrónico', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _passwordController,
                obscureText: true,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(labelText: 'Contraseña', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 20),
              authProvider.isLoading
                  ? const CircularProgressIndicator(color: AppTheme.gold)
                  : ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.gold,
                        foregroundColor: Colors.black,
                        minimumSize: const Size.fromHeight(48),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                      ),
                      onPressed: _login,
                      child: const Text('INICIAR SESIÓN', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                    ),
            ],
          ),
        ),
      ),
    );
  }
}
