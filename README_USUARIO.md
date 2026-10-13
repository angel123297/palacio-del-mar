# 🏰 PALACIO DEL MAR - Manual de Operación y Funcionamiento

¡Bienvenido al ecosistema tecnológico de Palacio del Mar! Este documento sirve como guía rápida de las capacidades operativas de la plataforma, un software de lujo diseñado a medida para operar bajo una lógica estricta y segura.

---

## 1. El Flujo de la Reserva (Guest Flow)
El sistema está pensado para funcionar 100% de manera desatendida desde la perspectiva del huésped:

1. **Búsqueda y Selección:** El cliente busca fechas. El sistema calcula en tiempo real los precios aplicando multiplicadores (Temporada alta x1.3, Pico Navideño x1.5, o media fin de semana x1.15). Si la reserva dura más de 7 días, se le otorga automáticamente un descuento del 5%.
2. **Servicios VIP:** Antes de pagar, el huésped puede seleccionar múltiples complementos para hacer su estadía inolvidable:
    * Traer mascota (Suma automática de $80.000 COP).
    * Solicitar una cuna (Sujeto a inventario global máximo de 5 cunas).
    * Recogida en aeropuerto ($150.000 COP).
    * Comida típica a la habitación (con o sin combo de desayuno).
3. **El Carrito "Timer" de 15 Minutos:** En cuanto el usuario empieza su proceso, el backend *aparta* la habitación (Anti-Overbooking). Si en 15 minutos exactos no efectúa el pago, un "Scheduler" autónomo en el servidor cancela la retención y devuelve la habitación al mercado.
4. **Pago y Fidelización:** Puede pagar el 100% o apartar con el 50%. En el resumen de pago también se incluye un "Depósito de Garantía" de $100.000 COP reembolsable. Además, el cliente acumula puntos por sus pagos, los cuales puede redimir como descuento en su próxima estadía.

## 2. El Flujo del Anfitrión (Host Flow)
El Anfitrión (Host) tiene acceso a un Dashboard especial para controlar la sede en tiempo real:

1. **Check-In y Cerraduras Inteligentes:** Una vez la reserva llega a estado pagado ("confirmed"), el sistema genera de forma aleatoria un PIN de 6 dígitos para el "Smart Lock" de la habitación. Este PIN es el que usará el usuario para entrar.
2. **Upgrade de Cortesía:** Si hay algún inconveniente (Ej. Protocolo Overstay donde el huésped anterior no sale a tiempo), el Host puede utilizar el botón de Upgrade para mover la reserva actual a otra Suite sin cambiar el costo, garantizando la buena experiencia.
3. **Late Check-Outs:** El Anfitrión hace el checkout del huésped. Si este proceso se realiza después de las 11:00 AM, el sistema genera inmediatamente un recargo del 50% de la noche y actualiza el saldo de la reserva automáticamente.

## 3. El Flujo de Administración y DevTools (Admin Flow)
Para propósitos de dirección y supervisión, los Administradores tienen acceso supremo:

1. **Revisión y Cancelaciones:** Pueden cancelar reservas y el sistema calculará una penalidad. ¡Atención! Si se cancela a menos de 48 horas de la llegada, la penalidad será del 100% (No-Show). De 3 a 7 días, será del 10%, y antes de eso es reembolso total.
2. **La Máquina del Tiempo (Exclusivo Localhost):** Para pruebas rápidas sin tener que esperar los 15 minutos del timer de expiración, el administrador cuenta con un botón de viaje en el tiempo. Al pulsarlo, el sistema engaña a la base de datos haciendo creer que el tiempo de todas las reservas pendientes ha pasado, forzando la liberación de habitaciones instantáneamente.
3. **Impresión de Facturas:** Se incorpora soporte nativo para generación de facturas / Vouchers impecables usando las capacidades de PDF e impresión del navegador.

---
*Desarrollado y Auditado con precisión. Todo el código actual ha sido verificado.*
