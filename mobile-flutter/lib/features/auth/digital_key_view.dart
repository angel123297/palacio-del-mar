import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../core/theme/app_theme.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/services/api_service.dart';
import '../../core/services/booking_stream_client.dart';
import 'dart:async';

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
  final Set<String> _revealedPins = {};
  int _selectedFilterIndex = 0;
  StreamSubscription? _bookingStreamSub;

  static final RegExp _emailRegExp = RegExp(
    r"^[a-zA-Z0-9.a-zA-Z0-9.!#$%&'*+-/=?^_`{|}~]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$",
  );

  bool _isValidEmail(String email) {
    return _emailRegExp.hasMatch(email.trim().toLowerCase());
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadBookings();
      _initBookingStream();
    });
  }

  void _initBookingStream() {
    final auth = context.read<AuthProvider>();
    if (!auth.isAuthenticated) return;
    BookingStreamClient().connect();
    _bookingStreamSub?.cancel();
    _bookingStreamSub = BookingStreamClient().bookingUpdates.listen((updated) {
      if (!mounted) return;
      final bookingId = updated['id'] ?? updated['_id'];
      setState(() {
        final idx = _userBookings.indexWhere((b) => (b is Map && (b['id'] == bookingId || b['_id'] == bookingId)));
        if (idx >= 0) {
          _userBookings[idx] = updated;
        } else {
          _userBookings.insert(0, updated);
        }
      });
    });
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
    _bookingStreamSub?.cancel();
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

  void _copyPin(String pin) {
    Clipboard.setData(ClipboardData(text: pin));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('PIN copiado al portapapeles'),
        backgroundColor: AppTheme.gold,
        duration: Duration(seconds: 2),
      ),
    );
  }

  void _confirmCancelBooking(String bookingId, String code) {
    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppTheme.cardBg,
        title: const Text('Cancelar Reserva', style: TextStyle(color: Colors.white)),
        content: Text(
          '¿Estás seguro de que deseas cancelar la reserva $code? Esta acción no se puede deshacer.',
          style: const TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx),
            child: const Text('VOLVER', style: TextStyle(color: Colors.white54)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.redAccent,
              foregroundColor: Colors.white,
            ),
            onPressed: () async {
              Navigator.pop(dialogCtx);
              try {
                await ApiService.cancelBooking(bookingId, reason: 'Cancelación solicitada por el usuario');
                if (!mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Reserva cancelada exitosamente'),
                    backgroundColor: AppTheme.gold,
                  ),
                );
                _loadBookings();
              } catch (_) {
                if (!mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Error al cancelar la reserva. Intenta de nuevo más tarde.'),
                    backgroundColor: Colors.redAccent,
                  ),
                );
              }
            },
            child: const Text('CANCELAR RESERVA'),
          ),
        ],
      ),
    );
  }

  void _showForgotPasswordDialog() {
    final resetController = TextEditingController();
    bool isSending = false;

    showDialog(
      context: context,
      builder: (dialogCtx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              backgroundColor: AppTheme.cardBg,
              title: const Text(
                'Recuperar Contraseña',
                style: TextStyle(color: AppTheme.gold, fontFamily: 'serif'),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Ingresa tu correo electrónico registrado y te enviaremos las instrucciones de recuperación.',
                    style: TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: resetController,
                    keyboardType: TextInputType.emailAddress,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(
                      labelText: 'Correo Electrónico',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.email_outlined, color: AppTheme.gold),
                    ),
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: isSending ? null : () => Navigator.pop(dialogCtx),
                  child: const Text('CANCELAR', style: TextStyle(color: Colors.white54, fontSize: 12)),
                ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.gold,
                    foregroundColor: Colors.black,
                  ),
                  onPressed: isSending
                      ? null
                      : () async {
                          final mail = resetController.text.trim().toLowerCase();
                          if (!_isValidEmail(mail)) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Ingresa un correo electrónico válido'),
                                backgroundColor: Colors.redAccent,
                              ),
                            );
                            return;
                          }
                          setDialogState(() => isSending = true);
                          final res = await ApiService.forgotPassword(mail);
                          if (!dialogCtx.mounted) return;
                          setDialogState(() => isSending = false);
                          Navigator.pop(dialogCtx);
                          if (!mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                res['message'] ??
                                    'Si el correo está registrado, recibirás un enlace de recuperación.',
                              ),
                              backgroundColor: AppTheme.gold,
                              behavior: SnackBarBehavior.floating,
                            ),
                          );
                        },
                  child: isSending
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black),
                        )
                      : const Text('ENVIAR ENLACE', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                ),
              ],
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();

    if (authProvider.isAuthenticated) {
      if (_userBookings.isEmpty && !_isLoadingBookings) {
        _loadBookings();
      }

      final activeBookings = _userBookings.where((b) {
        final status = (b is Map ? b['status'] : null)?.toString().toLowerCase();
        return status != 'cancelled' && status != 'cancelada';
      }).toList();

      final historicalBookings = _userBookings.where((b) {
        final status = (b is Map ? b['status'] : null)?.toString().toLowerCase();
        return status == 'cancelled' || status == 'cancelada';
      }).toList();

      final displayedBookings = _selectedFilterIndex == 0 ? activeBookings : historicalBookings;

      final initial = authProvider.displayName.isNotEmpty
          ? authProvider.displayName.substring(0, 1).toUpperCase()
          : 'H';

      return SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
        child: Center(
          child: Container(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // TARJETA DE PERFIL PROFESIONAL
                Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: AppTheme.cardBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.gold.withValues(alpha: 0.4)),
                  ),
                  child: Row(
                    children: [
                      CircleAvatar(
                        radius: 28,
                        backgroundColor: AppTheme.gold.withValues(alpha: 0.15),
                        child: Text(
                          initial,
                          style: const TextStyle(
                            fontFamily: 'serif',
                            color: AppTheme.gold,
                            fontSize: 26,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Flexible(
                                  child: Text(
                                    authProvider.displayName,
                                    style: const TextStyle(
                                      fontFamily: 'serif',
                                      color: Colors.white,
                                      fontSize: 18,
                                      fontWeight: FontWeight.bold,
                                    ),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: AppTheme.gold.withValues(alpha: 0.2),
                                    borderRadius: BorderRadius.circular(4),
                                    border: Border.all(color: AppTheme.gold.withValues(alpha: 0.6)),
                                  ),
                                  child: const Text(
                                    'HUÉSPED',
                                    style: TextStyle(color: AppTheme.gold, fontSize: 8, fontWeight: FontWeight.bold, letterSpacing: 1),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              authProvider.currentUserEmail ?? 'Sesión activa',
                              style: const TextStyle(color: Colors.white60, fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.logout_rounded, color: AppTheme.gold, size: 22),
                        tooltip: 'Cerrar Sesión',
                        onPressed: () => context.read<AuthProvider>().logout(),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 20),

                // SELECTOR DE PESTAÑAS: PRÓXIMAS / ACTIVAS vs HISTORIAL
                Container(
                  decoration: BoxDecoration(
                    color: AppTheme.cardBg,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: InkWell(
                          onTap: () => setState(() => _selectedFilterIndex = 0),
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: BoxDecoration(
                              color: _selectedFilterIndex == 0 ? AppTheme.gold.withValues(alpha: 0.15) : Colors.transparent,
                              borderRadius: BorderRadius.circular(8),
                              border: Border(
                                bottom: BorderSide(
                                  color: _selectedFilterIndex == 0 ? AppTheme.gold : Colors.transparent,
                                  width: 2,
                                ),
                              ),
                            ),
                            child: Text(
                              'PRÓXIMAS Y ACTIVAS (${activeBookings.length})',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: _selectedFilterIndex == 0 ? AppTheme.gold : Colors.white60,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 0.8,
                              ),
                            ),
                          ),
                        ),
                      ),
                      Expanded(
                        child: InkWell(
                          onTap: () => setState(() => _selectedFilterIndex = 1),
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: BoxDecoration(
                              color: _selectedFilterIndex == 1 ? AppTheme.gold.withValues(alpha: 0.15) : Colors.transparent,
                              borderRadius: BorderRadius.circular(8),
                              border: Border(
                                bottom: BorderSide(
                                  color: _selectedFilterIndex == 1 ? AppTheme.gold : Colors.transparent,
                                  width: 2,
                                ),
                              ),
                            ),
                            child: Text(
                              'HISTORIAL (${historicalBookings.length})',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: _selectedFilterIndex == 1 ? AppTheme.gold : Colors.white60,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 0.8,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 20),

                if (_isLoadingBookings) ...[
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 40),
                    child: Center(child: CircularProgressIndicator(color: AppTheme.gold)),
                  ),
                ] else if (displayedBookings.isEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 40),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(
                            _selectedFilterIndex == 0 ? Icons.hotel_outlined : Icons.history_outlined,
                            color: Colors.white24,
                            size: 48,
                          ),
                          const SizedBox(height: 14),
                          Text(
                            _selectedFilterIndex == 0
                                ? 'No tienes reservas activas por el momento'
                                : 'No tienes reservas en el historial',
                            style: const TextStyle(color: Colors.white60, fontSize: 14),
                          ),
                          const SizedBox(height: 16),
                          OutlinedButton.icon(
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTheme.gold,
                              side: const BorderSide(color: AppTheme.gold),
                            ),
                            icon: const Icon(Icons.refresh, size: 16),
                            onPressed: _loadBookings,
                            label: const Text('Actualizar'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ] else ...[
                  ...displayedBookings.map((booking) {
                    final bookingMap = booking is Map ? booking : <String, dynamic>{};
                    final suite = bookingMap['suite'];
                    final suiteName = (suite is Map ? suite['name'] : null) ??
                        bookingMap['guestName'] ??
                        'Suite Palacio del Mar';
                    final branch = bookingMap['branch'];
                    final location = (branch is Map ? branch['name'] : null) ?? 'Centro Histórico · Cartagena';
                    final bookingId = (bookingMap['id'] ?? bookingMap['_id'])?.toString() ?? '';
                    final code = (bookingMap['bookingCode'] ?? bookingId).toString();
                    final smartPin = bookingMap['smartLockPin']?.toString();
                    final checkIn = bookingMap['checkIn']?.toString();
                    final checkOut = bookingMap['checkOut']?.toString();
                    final dateRange = (checkIn != null && checkOut != null) ? '$checkIn al $checkOut' : null;
                    final status = (bookingMap['status']?.toString() ?? 'PENDIENTE').toUpperCase();
                    final isCancelled = status == 'CANCELLED' || status == 'CANCELADA';
                    final canCancel = !isCancelled;
                    final isPinRevealed = _revealedPins.contains(bookingId);
                    final foodOrders = bookingMap['foodOrders'] as List?;
                    final totalPrice = bookingMap['totalPrice']?.toString();

                    return Container(
                      margin: const EdgeInsets.only(bottom: 20),
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        color: const Color(0xFF161616),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: isCancelled ? Colors.white12 : AppTheme.gold.withValues(alpha: 0.3),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
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
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                decoration: BoxDecoration(
                                  color: isCancelled
                                      ? Colors.redAccent.withValues(alpha: 0.15)
                                      : AppTheme.gold.withValues(alpha: 0.15),
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(
                                    color: isCancelled
                                        ? Colors.redAccent.withValues(alpha: 0.5)
                                        : AppTheme.gold.withValues(alpha: 0.5),
                                  ),
                                ),
                                child: Text(
                                  status,
                                  style: TextStyle(
                                    color: isCancelled ? Colors.redAccent : AppTheme.gold,
                                    fontSize: 9,
                                    fontWeight: FontWeight.bold,
                                    letterSpacing: 1,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(location.toString(), style: const TextStyle(color: AppTheme.gold, fontSize: 12)),
                          if (dateRange != null) ...[
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                const Icon(Icons.calendar_today_outlined, size: 14, color: Colors.white60),
                                const SizedBox(width: 6),
                                Text(dateRange, style: const TextStyle(color: Colors.white70, fontSize: 12)),
                              ],
                            ),
                          ],
                          if (totalPrice != null) ...[
                            const SizedBox(height: 4),
                            Text(
                              'Total: \$ $totalPrice COP',
                              style: const TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.bold),
                            ),
                          ],
                          if (foodOrders != null && foodOrders.isNotEmpty) ...[
                            const SizedBox(height: 8),
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.04),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.restaurant, color: AppTheme.gold, size: 14),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: Text(
                                      'Incluye ${foodOrders.length} platillos ordenados',
                                      style: const TextStyle(color: Colors.white70, fontSize: 11),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],

                          if (!isCancelled) ...[
                            const Divider(height: 28, color: Colors.white10),
                            Center(
                              child: Column(
                                children: [
                                  const Text(
                                    'LLAVE DIGITAL DE ACCESO',
                                    style: TextStyle(
                                      color: AppTheme.gold,
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                      letterSpacing: 1.5,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  const Text(
                                    'Presenta este código en la cerradura inteligente',
                                    style: TextStyle(color: Colors.white38, fontSize: 10),
                                  ),
                                  const SizedBox(height: 12),
                                  Container(
                                    padding: const EdgeInsets.all(10),
                                    decoration: BoxDecoration(
                                      color: Colors.white,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: QrImageView(
                                      data: code,
                                      version: QrVersions.auto,
                                      size: 130,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 16),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: AppTheme.gold.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(6),
                                border: Border.all(color: AppTheme.gold.withValues(alpha: 0.3)),
                              ),
                              child: Column(
                                children: [
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      const Text(
                                        'PIN DE HABITACIÓN',
                                        style: TextStyle(
                                          color: AppTheme.gold,
                                          fontSize: 10,
                                          fontWeight: FontWeight.bold,
                                          letterSpacing: 1.5,
                                        ),
                                      ),
                                      if (smartPin != null) ...[
                                        const SizedBox(width: 8),
                                        InkWell(
                                          onTap: () {
                                            setState(() {
                                              if (isPinRevealed) {
                                                _revealedPins.remove(bookingId);
                                              } else {
                                                _revealedPins.add(bookingId);
                                              }
                                            });
                                          },
                                          child: Icon(
                                            isPinRevealed ? Icons.visibility_off : Icons.visibility,
                                            color: AppTheme.gold,
                                            size: 15,
                                          ),
                                        ),
                                        const SizedBox(width: 8),
                                        InkWell(
                                          onTap: () => _copyPin(smartPin),
                                          child: const Icon(Icons.copy_rounded, color: AppTheme.gold, size: 15),
                                        ),
                                      ],
                                    ],
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    smartPin != null
                                        ? (isPinRevealed ? smartPin : '••••••')
                                        : 'El PIN estará disponible al confirmar tu reserva',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontSize: smartPin != null ? 24 : 11,
                                      fontWeight: smartPin != null ? FontWeight.bold : FontWeight.normal,
                                      letterSpacing: smartPin != null ? (isPinRevealed ? 6 : 4) : 0.5,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],

                          if (canCancel && bookingId.isNotEmpty) ...[
                            const SizedBox(height: 12),
                            Align(
                              alignment: Alignment.centerRight,
                              child: TextButton.icon(
                                style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
                                icon: const Icon(Icons.cancel_outlined, size: 16),
                                label: const Text('Cancelar reserva', style: TextStyle(fontSize: 11)),
                                onPressed: () => _confirmCancelBooking(bookingId, code),
                              ),
                            ),
                          ],
                        ],
                      ),
                    );
                  }),
                ],
              ],
            ),
          ),
        ),
      );
    }

    // FORMULARIO DE LOGIN / REGISTRO
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Container(
          constraints: const BoxConstraints(maxWidth: 420),
          padding: const EdgeInsets.all(28),
          decoration: BoxDecoration(
            color: AppTheme.cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppTheme.gold.withValues(alpha: 0.3)),
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
                  _isRegister ? 'CREAR CUENTA DE HUÉSPED' : 'MI ESTANCIA & LLAVE DIGITAL',
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
                if (!_isRegister) ...[
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.only(top: 8, bottom: 4),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      onPressed: _showForgotPasswordDialog,
                      child: const Text(
                        '¿Olvidaste tu contraseña?',
                        style: TextStyle(color: Colors.white60, fontSize: 11),
                      ),
                    ),
                  ),
                ],
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
