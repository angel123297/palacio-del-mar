# 📱 Registro de Auditoría y Control — Aplicación Móvil Palacio del Mar (Noviembre 2026)

Documento dedicado exclusivamente al seguimiento de arquitectura, desarrollo, seguridad, pasarela de pago, sincronización y distribución de la **Aplicación Móvil (Flutter / Android / iOS PWA)** de Palacio del Mar.

---

## 📊 Resumen Ejecutivo
- **Tecnología**: Flutter 3.47 / Dart 3.13 / Provider / Clean Architecture
- **Compilaciones**:
  - **Android**: APK Release nativo (`build/app/outputs/flutter-apk/app-release.apk` y `frontend-angular/public/palacio-del-mar-release.apk`)
  - **iOS**: Progressive Web App optimizada con Service Worker y Safari Meta Tags desplegada en subruta `/app/`
- **Sincronización Bidireccional**: Enlazada en tiempo real con Backend Spring Boot 3 y MongoDB. Las reservas creadas en móvil bloquean la disponibilidad en la web y viceversa.
- **Pruebas Automatizadas**: 100% pasando en suite móvil (`flutter test test/suite_zone_filter_test.dart`).

---

## 🔍 Registro de Módulos y Funcionalidades Móviles

| ID | Componente Móvil | Descripción & Funcionalidad | Sincronización Web / Backend | Estado |
|---|---|---|---|:---:|
| **APP-01** | **Autenticación & Gmail** | Inicio de sesión con correo Gmail y registro automático. Se eliminaron datos redundantes dejando confirmación exclusiva de Nombre y Teléfono. | Conexión con `/api/auth` y asignación de JWT con roles Huésped / Anfitrión / Administrador. | ✅ **PRODUCCIÓN** |
| **APP-02** | **Catálogo de Suites & Disponibilidad en Vivo** | Vista `suites_view.dart` con badges visuales dinámicos: `DISPONIBLE` (verde) y `NO DISPONIBLE / OCUPADA` (rojo). Si una habitación está ocupada en la web, el botón cambia de `RESERVAR` a `AGOTADA` inhabilitando clics repetidos. | Consulta `/api/availability` en tiempo real. Bloqueo atómico de fechas por MongoDB. | ✅ **PRODUCCIÓN** |
| **APP-03** | **Stepper de Reserva en 4 Pasos** | Pantalla `booking_stepper_screen.dart` guiada:<br>1. Fechas y Estadía<br>2. Huéspedes y Opciones<br>3. Servicios Adicionales & Traslado<br>4. Datos de Contacto y Resumen con desglose de comisión (8%). | Valida montos y disponibilidad antes de crear la reserva en estado `PENDING_PAYMENT`. | ✅ **PRODUCCIÓN** |
| **APP-04** | **Pasarela de Pago Interactiva** | Pantalla `payment_screen.dart` con selector de pestañas interactivas:<br>1. **Tarjeta de Crédito / Débito**: Titular, Número de Tarjeta con limitante de 16 dígitos y formato `0000 0000 0000 0000`, Expiración `MM/AA`, CVV numérico (máx 4 dígitos) y logos Visa/Mastercard.<br>2. **PSE / Bancos**: Selector de entidades financieras de Colombia (Bancolombia, Davivienda, Banco de Bogotá, BBVA, etc.).<br>3. **Nequi**: Identidad fucsia oficial (#FF007F) y campo de número celular.<br>4. **Simulación de 4 Realidades Bancarias**: Cuadro interactivo con tarjetas de prueba `4242` (Aprobado), `0002` (Rechazado por banco), `9995` (Fondos insuficientes) y `0069` (Tarjeta expirada) con cuadros de diálogo de retroalimentación inmediata. | Protocolo idempotente en 2 fases (`/api/payments/checkout` y `/api/payments/confirm`) conectado con `SimulatedPaymentGateway`. Emite evento SSE para actualizar paneles de administración. | ✅ **PRODUCCIÓN** |
| **APP-05** | **Temporizador de Retención (15 Minutos TTL)** | Cuenta regresiva de 15:00 minutos visible en cabecera del pago (`Tiempo restante para pagar tu reserva: MM:SS`). Si vence, cancela la sesión temporal y libera las fechas para otros usuarios. | Alineado con el motor de expiración de reservas del backend. | ✅ **PRODUCCIÓN** |
| **APP-06** | **Comprobante Digital & PIN Cerradura Inteligente** | Pantalla de confirmación con generación de PIN numérico de acceso inteligente (ej. `182786`), código QR de check-in, desglose de IVA (19%) y botón de impresión / descarga en PDF. | La reserva se refleja de inmediato en la sección "Mis Reservas" de la web y en los paneles de Anfitrión y Administrador. | ✅ **PRODUCCIÓN** |
| **APP-07** | **Distribución Android & iOS** | - **Android**: Botón y enlace directo al archivo `APK Palacio Del Mar` (`/palacio-del-mar-release.apk`).<br>- **iOS**: Enlace único para abrir la aplicación web progresiva optimizada (`/app/`). | Servido a través del servidor Nginx seguro con CSP y compresión gzip. | ✅ **PRODUCCIÓN** |

---

## 🧪 Pruebas y Verificación

1. **Pruebas Unitarias Flutter**:
   - `flutter test test/suite_zone_filter_test.dart` -> 4 tests exitosos (100% pasando).
2. **Generación APK**:
   - `flutter build apk --release` -> APK generado en `build/app/outputs/flutter-apk/app-release.apk` (56.4 MB).
3. **Generación Web PWA**:
   - `flutter build web --release --base-href "/app/"` -> Bundle optimizado servido en Nginx `/app/`.
