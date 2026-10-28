import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../core/theme/app_theme.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/services/api_service.dart';

class DigitalKeyView extends StatefulWidget {
  const DigitalKeyView({super.key});

  @override
  State<DigitalKeyView> createState() => _DigitalKeyViewState();
}

class _DigitalKeyViewState extends State<DigitalKeyView> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _isRegister = false;
  bool _obscurePassword = true;
  List<dynamic> _userBookings = [];
  bool _isLoadingBookings = false;

  static final RegExp _emailRegExp = RegExp(
    r"^[a-zA-Z0-9.a-zA-Z0-9.!#$%&'*+-/=?^_`{|}~]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$",
  );

  bool _isValidEmail(String email) {
    return _emailRegExp.hasMatch(email.trim().toLowerCase());
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadBookings());
  }

  Future<void> _loadBookings() async {
    final auth = context.read<AuthProvider>();
    if (!auth.isAuthenticated) return;
    setState(() => _isLoadingBookings = true);
    final data = await ApiService.getMyReservations();
    if (!mounted) return;
    setState(() {
      _userBookings = data;
      _isLoadingBookings = false;
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (!_formKey.currentState!.validate()) return;

    final email = _emailController.text.trim().toLowerCase();
    final password = _passwordController.text.trim();
    final name = _nameController.text.trim();

    final authProvider = context.read<AuthProvider>();

    if (_isRegister) {
      final res = await authProvider.register(name, email, password);
      if (!mounted) return;

      if (res['success'] == true) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(res['message'] ?? 'Revisa tu correo para verificar tu cuenta antes de iniciar sesión'),
            backgroundColor: AppTheme.gold,
            behavior: SnackBarBehavior.floating,
          ),
        );
        setState(() {
          _isRegister = false;
          _passwordController.clear();
        });
      } else {
        final errorMsg = res['statusCode'] == 409
            ? (res['message'] ?? 'El correo ya está registrado')
            : (res['message'] ?? 'Error al procesar el registro');
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMsg),
            backgroundColor: Colors.redAccent,
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } else {
      final res = await authProvider.login(email, password);
      if (!mounted) return;

      if (res['success'] == true) {
        _loadBookings();
      } else {
        final errorMsg = res['message'] ?? 'Credenciales incorrectas o error de conexión';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMsg),
            backgroundColor: Colors.redAccent,
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();

    if (authProvider.isAuthenticated) {
      if (_userBookings.isEmpty && !_isLoadingBookings) {
        _loadBookings();
      }
      final latest = _userBookings.isNotEmpty ? _userBookings.first : null;
      final suite = latest is Map ? latest['suite'] : null;
      final suiteName = (suite is Map ? suite['name'] : null) ?? (latest is Map ? latest['guestName'] : null) ?? 'Palacio del Mar';
      final branch = latest is Map ? latest['branch'] : null;
      final location = (branch is Map ? branch['name'] : null) ?? authProvider.currentUserEmail ?? 'Centro Histórico · Cartagena';
      final qrCode = (latest is Map ? (latest['bookingCode'] ?? latest['id']) : null)?.toString() ?? 'PALACIO-KEY';
      final smartPin = latest is Map ? latest['smartLockPin']?.toString() : null;
      final checkIn = latest is Map ? latest['checkIn']?.toString() : null;
      final checkOut = latest is Map ? latest['checkOut']?.toString() : null;
      final dateRange = (checkIn != null && checkOut != null) ? '$checkIn - $checkOut' : null;

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
                    Expanded(
                      child: Text(
                        suiteName.toString(),
                        style: const TextStyle(
                          fontFamily: 'serif',
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.logout, color: Colors.white54, size: 20),
                      onPressed: () => context.read<AuthProvider>().logout(),
                    )
                  ],
                ),
                Text(
                  location.toString(),
                  style: const TextStyle(color: AppTheme.gold, fontSize: 12),
                ),
                if (dateRange != null) ...[
                  const SizedBox(height: 6),
                  Text(
                    dateRange,
                    style: const TextStyle(color: Colors.white70, fontSize: 11),
                  ),
                ],
                const Divider(height: 30, color: Colors.white10),
                const Text(
                  'LLAVE DIGITAL DE ACCESO',
                  style: TextStyle(
                    color: AppTheme.gold,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 2,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Presenta este código en la cerradura inteligente',
                  style: TextStyle(color: Colors.white38, fontSize: 10),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: QrImageView(
                    data: qrCode,
                    version: QrVersions.auto,
                    size: 160,
                  ),
                ),
                const SizedBox(height: 20),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.gold.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: AppTheme.gold.withOpacity(0.3)),
                  ),
                  child: Column(
                    children: [
                      const Text(
                        'PIN DE HABITACIÓN',
                        style: TextStyle(
                          color: AppTheme.gold,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 2,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        smartPin ?? 'El PIN estará disponible al confirmar tu reserva',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: smartPin != null ? 32 : 12,
                          fontWeight: smartPin != null ? FontWeight.bold : FontWeight.normal,
                          letterSpacing: smartPin != null ? 6 : 1,
                        ),
                      ),
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
          constraints: const BoxConstraints(maxWidth: 420),
          padding: const EdgeInsets.all(28),
          decoration: BoxDecoration(
            color: AppTheme.cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppTheme.gold.withOpacity(0.3)),
          ),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                  'Palacio del Mar',
                  style: TextStyle(
                    fontFamily: 'serif',
                    color: AppTheme.gold,
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  _isRegister ? 'CREAR CUENTA DE HUÉSPED' : 'LLAVE DIGITAL & ACCESO A SUITES',
                  style: const TextStyle(
                    color: Colors.white54,
                    fontSize: 10,
                    letterSpacing: 1.5,
                  ),
                ),
                const SizedBox(height: 24),
                if (_isRegister) ...[
                  TextFormField(
                    controller: _nameController,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(
                      labelText: 'Nombre Completo',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.person_outline, color: AppTheme.gold),
                    ),
                    validator: (v) {
                      if (v == null || v.trim().isEmpty) {
                        return 'Ingresa tu nombre';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 14),
                ],
                TextFormField(
                  controller: _emailController,
                  keyboardType: TextInputType.emailAddress,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Correo Electrónico',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.email_outlined, color: AppTheme.gold),
                  ),
                  validator: (v) {
                    if (v == null || v.trim().isEmpty) {
                      return 'Ingresa tu correo';
                    }
                    if (!_isValidEmail(v)) {
                      return 'Formato de correo no válido';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 14),
                TextFormField(
                  controller: _passwordController,
                  obscureText: _obscurePassword,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    labelText: 'Contraseña',
                    border: const OutlineInputBorder(),
                    prefixIcon: const Icon(Icons.lock_outline, color: AppTheme.gold),
                    suffixIcon: IconButton(
                      icon: Icon(
                        _obscurePassword ? Icons.visibility_off : Icons.visibility,
                        color: Colors.white38,
                      ),
                      onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                    ),
                  ),
                  validator: (v) {
                    if (v == null || v.trim().isEmpty) {
                      return 'Ingresa tu contraseña';
                    }
                    if (_isRegister && v.trim().length < 6) {
                      return 'Mínimo 6 caracteres';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 24),
                authProvider.isLoading
                    ? const CircularProgressIndicator(color: AppTheme.gold)
                    : ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppTheme.gold,
                          foregroundColor: Colors.black,
                          minimumSize: const Size.fromHeight(48),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                        ),
                        onPressed: _submit,
                        child: Text(
                          _isRegister ? 'REGISTRARSE' : 'INICIAR SESIÓN',
                          style: const TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5),
                        ),
                      ),
                const SizedBox(height: 16),
                TextButton(
                  onPressed: () {
                    setState(() {
                      _isRegister = !_isRegister;
                      _formKey.currentState?.reset();
                    });
                  },
                  child: Text(
                    _isRegister
                        ? '¿YA TIENES CUENTA? INICIA SESIÓN'
                        : '¿NO TIENES CUENTA? REGÍSTRATE AQUÍ',
                    style: const TextStyle(color: AppTheme.gold, fontSize: 11, letterSpacing: 1),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
