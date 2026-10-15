# Reporte de QA - Proyecto Palacio del Mar

## 1. Resumen Ejecutivo
Se realizó un test intensivo de todo el ecosistema (backend y frontend). Ambos entornos han pasado de manera exitosa todas las pruebas automatizadas (93/93 en backend, 8/8 en frontend). Adicionalmente, se identificaron y corrigieron inconsistencias arquitectónicas y de seguridad en el código.

## 2. Inconsistencias Encontradas y Corregidas

### 2.1. Duplicación de código y Bypass de la Máquina de Estados (HostController)
**Problema:** En el archivo `backend-java/src/main/java/co/palaciodelmar/backend/controller/HostController.java`, los métodos `checkOut` y `checkIn` modificaban el estado de la reserva directamente (ej: `booking.setStatus("completed")` y `booking.setStatus("checked_in")`) e incluso en el checkout se encontraba el código duplicado (`booking.setStatus("completed"); booking.setStatus("completed");`). Esto es una inconsistencia ya que bypassa por completo la máquina de estados y el historial de cambios gestionado por el método `changeBookingStatus` en `BookingService`.

**Solución:** Se refactorizó `HostController` para utilizar `bookingService.changeBookingStatus(booking, BookingStatus.CHECKED_IN, userPrincipal.getUser().getId(), "Check-in by host")` y para `CHECKED_OUT` (el cual es el estado correcto en el Enum de `BookingStatus`, y no el literal `"completed"` o `"checked_in"`). Ahora las transiciones se reflejarán correctamente en el log histórico de la reserva de acuerdo al diseño arquitectónico.

### 2.2. Riesgo de Seguridad: JWT Secret por defecto
**Problema:** En `backend-java/src/main/resources/application.properties`, existía un valor por defecto inseguro (`jwt.secret=${JWT_SECRET:super_secret_development_key_only_for_local_use_change_me_in_prod}`). Esta práctica en producción es muy riesgosa si la variable de entorno llegara a estar ausente.

**Solución:** Se eliminó el valor quemado de desarrollo. Ahora la aplicación exigirá que se defina siempre la variable de entorno `JWT_SECRET` en el ambiente en el que se ejecuta (`jwt.secret=${JWT_SECRET}`).

## 3. Resultados de Pruebas Automatizadas

**Backend (Java/Spring Boot):**
- Tests run: 93
- Failures: 0
- Errors: 0
- *BUILD SUCCESS*

**Frontend (Angular 18/Vitest):**
- Test Files: 4 passed
- Tests: 8 passed
- *All passing*

**Conclusión:**
Los flujos principales de reservas, validaciones, JWT y controladores host se encuentran completamente funcionales, validados y consistentes con la arquitectura limpia del proyecto.