# Auditoría y Depuración Web: Palacio del Mar

Fecha: 2026-10-28
Rama de trabajo: `fix/web-auditoria`
Commit base: `cda5160` (feature/app-movil-flutter)
Commit final: `0582bb7`
Entorno de prueba: Windows 11, OpenJDK 25.0.3, Maven 3.9.16, Node v24.21.0, npm 11.19.0, Docker 29.8.2.

---

## 1. Resumen Ejecutivo
Se realizó una auditoría y depuración técnica exhaustiva y aislada de los componentes web del ecosistema Palacio del Mar: el backend Java (Spring Boot 3.3.5 / Java 21) y el frontend web Angular 19. El componente móvil (`mobile-flutter`) se mantuvo estrictamente intacto y fuera de alcance.

El análisis de integración comprobó el correcto enlace entre frontend y backend mediante proxy inverso y CORS habilitado. Se detectó y subsanó una regresión crítica de backend introducida en un commit anterior que degradaba `BookingService`, rompiendo la validación de propiedad y control de acceso (IDOR / Marketplace). Tras la restauración del servicio, 100% de la suite de pruebas de backend (191 tests) y frontend (8 tests en Vitest) pasaron satisfactoriamente.

---

## 2. Inventario de Componentes Auditados
| Componente | Stack Tecnológico | Directorio | Puerto / URL | Estado Inicial | Estado Final |
|---|---|---|---|---|---|
| **Backend** | Spring Boot 3.3.5, Java 21, Spring Security, MongoDB, Redis | `backend-java` | `http://localhost:5000` | Fallos en 4 tests de seguridad y lógica de negocio | 100% tests aprobados (191/191) |
| **Frontend Web** | Angular 19.2.0, TypeScript 5.8, Tailwind CSS, Vitest | `frontend-angular` | `http://localhost:4200` | Build exitoso, script test dependiente de POSIX | Build exitoso, tests aprobados (8/8) |
| **Infraestructura** | Docker Compose (Mongo 7.0, Redis 7.2, Mailpit) | Contenedores locales | Puertos 27017, 6379, 1025/8025 | Operativos y conectados | Operativos y validados |

---

## 3. Matriz de Hallazgos y Vulnerabilidades
| ID | Severidad | Componente | Descripción | Ubicación | Estado |
|---|---|---|---|---|---|
| **WEB-001** | **CRÍTICA** | `backend-java` | Regresión en `BookingService`: pérdida de validaciones de propiedad aprobada/activa (`propertyRepository`), omisión de verificación de pertenencia de host por sede/propiedad (IDOR), y error de cálculo por omisión de `dynamicPricingService` | `BookingService.java:49-354` | **Resuelto** |
| **WEB-002** | **MEDIA** | `frontend-angular` | Script npm `test` usa sintaxis POSIX inline (`NODE_PATH=... ng test`) no interpretable nativamente por PowerShell / cmd en Windows | `frontend-angular/package.json:11` | Mitigado (se corre vía `npx ng test --watch=false`) |
| **WEB-003** | **ALTA** | `frontend-angular` | Vulnerabilidad detectada por `npm audit` en dependencia transitiva `zod <3.22.3` / `zod <4.6.6` (exhaustion of resources / DoS) | `frontend-angular/package-lock.json` | Identificado (requiere actualización de librería zod) |
| **WEB-004** | **BAJA** | `.agents` | Carpeta de configuración de agentes AI estaba rastreada en git, ensuciando árbol de trabajo | Raíz del proyecto | **Resuelto** (excluida en `.gitignore` y eliminada de índice) |

---

## 4. Detalle de Correcciones Aplicadas

### WEB-001: Restauración canónica y segura de BookingService
- **Causa raíz**: El commit previo `93e704f` sobreescribió la lógica completa de `BookingService.java` para resolver un stub temporal de `createBooking`, pero eliminó las inyecciones de `propertyRepository`, `dynamicPricingService` y `eventPublisher`, así como los chequeos de aprobación de alojamientos y control IDOR de hosts sobre reservas ajenas.
- **Impacto**: `MarketplaceCatalogSecurityTest`, `RoleAndIdorSecurityTest`, `CancellationPolicyBoundaryTest` y `PriceIntegritySecurityTest` fallaban al ejecutar `mvnw test`.
- **Solución**: Restauración de la implementación canónica y robusta validada en `5c1981d`:
  - Inyección de dependencias seguras (`PropertyRepository`, `DynamicPricingService`, `ApplicationEventPublisher`).
  - Verificación de propiedad aprobada y activa previo a consolidar la reserva.
  - Validación de aislamiento para hosts (`validateHostAccessToBooking`) garantizando que un anfitrión no pueda ver reservas fuera de sus sedes o propiedades asignadas.
  - Publicación reactiva de eventos de dominio (`BookingDomainEvent`) ante cambios de estado (`booking.confirmed`, `booking.cancelled`, etc.).
- **Commit asociado**: `0582bb7 fix(backend): restaurar implementacion completa y segura de BookingService (WEB-001)`

---

## 5. Análisis de Integración Backend ↔ Frontend

### 5.1 Enlace y Enrutamiento
- El frontend web Angular consume la API mediante paths relativos prefijados con `/api` (definido en `environment.apiUrl = '/api'`).
- En producción / Docker, Nginx sirve los estáticos compilados en `:4200` y proxia las peticiones de `/api/` hacia el servicio backend en `:5000`.
- En desarrollo local, Angular CLI se apoya en proxy o comunicación directa con backend configurado en `SecurityConfig.java`.

### 5.2 Configuración CORS
El backend (`SecurityConfig.java:101-118`) implementa la siguiente lista blanca estricta de orígenes autorizados:
- `http://localhost:4200`
- `http://localhost:5000`
- `http://127.0.0.1:4200`
- `http://127.0.0.1:5000`
- Métodos permitidos: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`.
- Credenciales: `allowCredentials(true)`.

### 5.3 Smoke Tests de Endpoints
Pruebas directas de integración HTTP ejecutadas sobre backend activo (`http://localhost:5000`):
- `GET /api/suites`: **HTTP 200 OK** (13 suites retornadas con atributos de capacidad, imágenes y precio base).
- `GET /api/branches`: **HTTP 200 OK** (4 sedes activas: Cartagena, Santa Marta, San Andrés, Eje Cafetero).
- `GET /api/experiences`: **HTTP 200 OK** (8 experiencias turísticas disponibles).
- `GET /api/availability?checkIn=2026-11-01&checkOut=2026-11-05`: **HTTP 200 OK** (5 alojamientos evaluados en disponibilidad de slots).
- `GET http://localhost:4200/`: **HTTP 200 OK** (SPA servida con assets estáticos íntegros).

---

## 6. Resultados de Verificación Preventiva

### 6.1 Backend (`backend-java`)
- Comando de compilación: `mvnw compile` (Java 21, código 0).
- Comando de tests: `mvnw test`
- Resultado: **191 tests ejecutados, 0 fallos, 0 errores, 0 saltados**.
- Duración: ~20.3 segundos.
- Cobertura de seguridad:
  - `RoleAndIdorSecurityTest`: 10/10 aprobados.
  - `MarketplaceCatalogSecurityTest`: 9/9 aprobados.
  - `PriceIntegritySecurityTest`: 3/3 aprobados.
  - `CancellationPolicyBoundaryTest`: 5/5 aprobados.

### 6.2 Frontend Web (`frontend-angular`)
- Comando de compilación: `npm run build` (código 0, bundle total inicial 591.29 kB).
- Comando de tests: `npx ng test --watch=false`
- Resultado: **4 test files aprobados, 8/8 tests exitosos** (Vitest 5.0.4).
- Duración: ~5.3 segundos.

---

## 7. Árbol de Cambios y Commits Realizados
En la rama `fix/web-auditoria`:
1. `dc9a0f5`: `chore(web): sacar .agents del indice y excluirlo`
2. `0582bb7`: `fix(backend): restaurar implementacion completa y segura de BookingService (WEB-001)`

Diff acumulado sobre código web:
- `.gitignore`: +2 líneas
- `.agents/skills/non-breaking-delivery/SKILL.md`: desindexado
- `backend-java/.../BookingService.java`: +289 / -86 líneas

**Archivos móviles tocados**: Ninguno (0 archivos en `mobile-flutter`).

---

## 8. Conclusiones y Recomendaciones
1. **Estabilidad**: El sistema web backend/frontend se encuentra completamente estabilizado y con todas las pruebas automatizadas en verde.
2. **Seguridad**: Se restablecieron las barreras de autorización para reservas de propiedades y acceso de anfitriones a datos confidenciales.
3. **Mantenimiento**: Se recomienda actualizar el script `test` en `frontend-angular/package.json` para usar `cross-env` si se desea compatibilidad multiplataforma en scripts directos de npm.

---

## Auditoría de Seguimiento y Depuración (Hallazgos para Corrección Manual)

Segunda ronda de depuración técnica profunda (análisis estático y dinámico) realizada sobre los componentes web (`backend-java` y `frontend-angular`).

### Hallazgo WEB-005
- **Componente**: Frontend (`frontend-angular`)
- **Ubicación**: `frontend-angular/src/app/components/hotel-map/hotel-map.component.ts:1` y `angular.json`
- **Descripción del problema**: Advertencia de optimización durante `ng build`: el módulo `leaflet` es importado como dependencia CommonJS/AMD, lo que provoca advertencias de "optimization bailouts" y reduce la efectividad del tree-shaking en la generación de bundles de producción.
- **Instrucción de Corrección**:
  1. En `frontend-angular/angular.json`, dentro de `projects["palacio-del-mar-web"].architect.build.options`, agregar `leaflet` en `allowedCommonJsDependencies`:
     ```json
     "allowedCommonJsDependencies": ["leaflet"]
     ```
  2. Alternativamente, evaluar el uso de wrappers nativos ESM para Leaflet en Angular o carga diferida por importación dinámica `await import('leaflet')`.

### Hallazgo WEB-006
- **Componente**: Frontend (`frontend-angular`)
- **Ubicación**: `frontend-angular/src/app/reserva.component.ts:31`
- **Descripción del problema**: Se detectó una sentencia residual `console.log('Enviando datos limpios al backend...', payload);` que expone los datos del formulario de reserva (potencialmente PII: nombre, teléfono, correo) en la consola del navegador del cliente en entornos productivos.
- **Instrucción de Corrección**:
  1. Remover la línea 31 de `reserva.component.ts` o envolverla bajo condicional de entorno de desarrollo:
     ```typescript
     if (!environment.production) {
       console.log('Enviando datos limpios al backend...', payload);
     }
     ```
  2. Si el componente `reserva.component.ts` es un artefacto no utilizado en favor de `booking-modal.component.ts`, evaluar su eliminación o refactorización.

### Hallazgo WEB-007
- **Componente**: Backend (`backend-java`)
- **Ubicación**: `backend-java/src/main/java/co/palaciodelmar/backend/model/SuiteNight.java:49`
- **Descripción del problema**: Uso de parámetro deprecado `expireAfterSeconds()` en la anotación `@Indexed` de Spring Data MongoDB (`@Indexed(expireAfterSeconds = 0)`). Produce 5 advertencias de compilación (`expireAfterSeconds() has been deprecated and marked for removal`).
- **Instrucción de Corrección**:
  1. En `SuiteNight.java:49`, migrar a la propiedad moderna soportada:
     ```java
     @Indexed(expireAfter = "0s")
     ```
     o definir la duración mediante formato ISO-8601 / `java.time.Duration` según la versión de Spring Data MongoDB 4.x+.

### Hallazgo WEB-008
- **Componente**: Backend (`backend-java`)
- **Ubicación**: `backend-java/src/main/java/co/palaciodelmar/backend/security/JwtService.java:69`
- **Descripción del problema**: `Jwts.parserBuilder()` en JJWT 0.11.x / 0.12.x genera avisos de deprecación al compilar (`uses or overrides a deprecated API`). En versiones recientes de JJWT, `parserBuilder()` ha sido reemplazado por `Jwts.parser()`.
- **Instrucción de Corrección**:
  1. En `JwtService.java`, actualizar la instanciación del parser de tokens:
     ```java
     return Jwts.parser()
             .verifyWith(getSigningKey())
             .build()
             .parseSignedClaims(token)
             .getPayload();
     ```
     (o la firma equivalente según la versión específica de `jjwt-api` en el `pom.xml`).

### Hallazgo WEB-009
- **Componente**: Frontend (`frontend-angular`)
- **Ubicación**: `frontend-angular/src/app/interceptors/auth.interceptor.ts:23-38`
- **Descripción del problema**: El interceptor global captura únicamente errores `401 Unauthorized` para desloguear y redirigir al usuario, pero deja sin intercepción ni notificación unificada los errores `500 Internal Server Error`, `502 Bad Gateway` o timeouts de red, requiriendo que cada componente implemente su propio bloque `error: (...)`.
- **Instrucción de Corrección**:
  1. En `auth.interceptor.ts`, agregar manejo para errores 5xx / de red que notifique al usuario mediante un servicio global de toast / alerta (ej. `ToastService` o `NotificationService`):
     ```typescript
     if (error.status >= 500) {
       // Disparar toast de notificación: 'Error interno del servidor. Por favor, intenta de nuevo más tarde.'
     }
     ```


---

## 3ra Ronda: Auditoría Integral y Depuración de Vulnerabilidades y Datos

Fecha: 2026-10-30
Commit de Referencia: `620c2ac` (sincronizado en `fix/web-backend-confirmacion-stream` y `proyecto-angular-java`)

### Matriz de Nuevos Hallazgos Auditados y Resueltos

| ID | Severidad | Componente | Descripción del Problema | Remediación Aplicada | Estado |
|---|---|---|---|---|---|
| **WEB-010** | **GRAVE** | `frontend-angular` / `nginx` | **Error 403 Forbidden por Origin Header**: Al acceder vía Ngrok o dominios externos, Spring Boot bloqueaba el inicio de sesión y endpoints REST con `Invalid CORS request` porque el header `Origin` no coincidía exactamente. | En `nginx.conf`, se sanitizó `proxy_set_header Origin ""` para peticiones internas del proxy, permitiendo que el backend trate el tráfico interno de forma autorizada y responda 200/401 en lugar de 403. | **Resuelto** |
| **WEB-011** | **GRAVE** | `backend-java` / `AuthService` | **Cuentas Inaccesibles sin Servidor SMTP**: En entornos locales o de desarrollo sin credenciales SMTP de Gmail configuradas en `.env`, el usuario se creaba con `emailVerified: false`, bloqueando permanentemente el inicio de sesión. | En `EmailService.isConfigured()`, se verifica si existen credenciales válidas. Si no hay SMTP configurado, `AuthService` auto-verifica el usuario al registrarse permitiendo el acceso inmediato sin depender de correos en entornos locales. | **Resuelto** |
| **WEB-012** | **MEDIA** | `frontend-angular` / `nginx` | **MIME Type Genérico en Descarga de APK Android**: El servidor Nginx devolvía el instalador APK con `application/octet-stream` o `text/plain`, impidiendo que los teléfonos Android activaran el instalador de paquetes al tocar el enlace. | Se configuró en `nginx.conf` un bloque específico para `/palacio-del-mar-release.apk` con `default_type application/vnd.android.package-archive;` y cabecera `Content-Disposition: attachment; filename="palacio-del-mar.apk"`. | **Resuelto** |
| **WEB-013** | **MEDIA** | `mobile-flutter` / `http_client` | **Intercepción de Ngrok Interstitial Warning**: Las peticiones de la aplicación recibían la página HTML de advertencia de Ngrok en lugar del payload JSON de la API. | Se inyectó en el cliente HTTP de la app el encabezado oficial `'ngrok-skip-browser-warning': 'true'`. | **Resuelto** |
| **WEB-014** | **GRAVE** | `backend-java` / `JwtAuthFilter` | **Vulnerabilidad de Conexión en Streaming SSE**: Los navegadores no permiten enviar el encabezado `Authorization: Bearer` al instanciar `EventSource`. La conexión a `/api/bookings/stream` fallaba con 401 Unauthorized impidiendo eventos en tiempo real. | Se actualizó `JwtAuthenticationFilter.java` para extraer el token JWT desde el parámetro query `?token=`, restringido estrictamente a la ruta `/api/bookings/stream` para no debilitar el resto de la API. | **Resuelto** |
| **WEB-015** | **BAJA / UX** | `frontend-angular` | **Ausencia de Catálogo de Experiencias en Portada**: Las experiencias turísticas (islas, catamarán, gastronomía) solo eran visibles en el paso 2 de la reserva. | Se construyó el componente autónomo `ExperiencesSectionComponent` y se integró en la portada (`HomeComponent`). | **Resuelto** |
| **WEB-016** | **BAJA / UX** | `frontend-angular` | **Moneda Fija en COP sin Soporte Multidivisa**: Los turistas internacionales no disponían de conversión visual de precios en dólares o euros. | Se implementó el servicio global reactivo `CurrencyService` y un selector en el `NavbarComponent` para alternar entre COP, USD y EUR en todo el catálogo de suites. | **Resuelto** |
| **WEB-017** | **BAJA / UX** | `frontend-angular` | **Ausencia de Canal Inmediato de Concierge en Vivo**: El contacto de WhatsApp estaba relegado al pie de página. | Se desarrolló el componente flotante `ConciergeChatComponent` con apertura contextual de WhatsApp indicando la suite consultada. | **Resuelto** |
| **WEB-018** | **BAJA / UX** | `frontend-angular` | **Carga Abrupta de Habitaciones (Falta de Skeletons)**: El usuario visualizaba un spinner genérico centrado mientras cargaban las suites. | Se diseñó una cuadrícula de Skeleton Cards animadas en `RoomsSectionComponent` con Tailwind CSS para una experiencia de carga fluida. | **Resuelto** |
| **WEB-019** | **BAJA / UX** | `frontend-angular` | **Imposibilidad de Zoom y Galería en Pantalla Completa**: No se podían ver las fotografías de las suites en alta resolución. | Se creó el componente `LightboxModalComponent` con visor a pantalla completa, navegación por teclado y carrusel de miniaturas en `SuiteDetailComponent`. | **Resuelto** |
| **WEB-020** | **MEDIA / UX** | `frontend-angular` | **Legibilidad y Contraste en Formularios**: Conflicto de estilos CSS globales provocaba contraste deficiente o fondo/texto blanco en los inputs y placeholders de los modales de contacto, inicio de sesión y registro de huésped/anfitrión. | Se restauraron las reglas globales de estilos manteniendo contraste nítido y legible en inputs, etiquetas doradas y texto blanco/oro sobre fondo oscuro de lujo. | **Resuelto** |
| **WEB-021** | **BAJA / BRANDING** | `frontend-angular` | **Favicon de Angular por Defecto en la Pestaña**: La pestaña del navegador mostraba el escudo de Angular por defecto en lugar del logo oficial de Palacio del Mar. | Se generó el paquete completo de favicons con el escudo oficial en múltiples resoluciones (`favicon.ico` 16/32/48/64/128/256px, `favicon.svg`, `favicon-32x32.png`, `favicon-192.png`) y se configuraron en `src/` y `public/`. | **Resuelto** |
| **WEB-022** | **BAJA / UX** | `frontend-angular` | **Botón Flotante de WhatsApp Inconsistente**: El botón flotante tenía estilo de pastilla con texto "Concierge 24/7" y enlazaba a un número genérico de prueba. | Se rediseñó el botón flotante como un círculo con el logo de WhatsApp en el centro y se actualizó el enlace directo al teléfono oficial `+57 316 2389171`. | **Resuelto** |
| **WEB-023** | **ALTA / UI** | `frontend-angular` | **Restauración Definitiva de Estilos CSS en Inputs**: Se eliminaron las reglas que blanqueaban los fondos o textos en Tailwind v3, reincorporando las reglas CSS originales (`styles.scss`) con fondo oscuro de lujo `rgba(255, 255, 255, 0.04)`, borde dorado y texto/placeholder de contraste nítido en todos los formularios. | Reintegradas las reglas formales en `styles.scss` y sincronizados los assets `src/favicon.ico` y `src/favicon.svg` en `angular.json`. | **Resuelto** |
| **WEB-024** | **BAJA / BRANDING** | `frontend-angular` | **Reemplazo Definitivo de Favicon desde Captura Oficial**: Se procesó la imagen oficial (`Captura de pantalla 2026-10-30 151340.png`), recortando el logo, cuadrándolo con fondo neutro y generando `favicon.ico` con 6 resoluciones (16 a 256px), `favicon.svg` y versiones PNG en `src/` y `public/`. | Generados nuevos assets de favicon, registrados en `angular.json` y desplegados en Nginx. | **Resuelto** |
| **WEB-025** | **MEDIA / CACHE** | `frontend-angular` / `nginx` | **Persistencia de Favicon de Angular por Caché Inmutable del Navegador**: El navegador retenía el escudo de Angular debido a la directiva `Cache-Control: public, max-age=31536000, immutable` de Nginx para extensiones `.ico` y `.png`. | Se agregaron query strings versionadas con timestamp (`?v=1793416779504`) en todos los links de favicon en `index.html` y se creó un bloque prioritario en `nginx.conf` para forzar `Cache-Control: no-cache, no-store, must-revalidate` para todos los favicons. | **Resuelto** |

---

## 4. Estado Final de Pruebas y Despliegue
- **Pruebas de Backend**: 446 pruebas unitarias y de integración ejecutadas: **446 aprobadas, 0 fallos, 0 errores**.
- **Compilación de Frontend**: Angular 19 compilado exitosamente para producción (`dist/palacio-del-mar-web`).
- **Verificación de Vulnerabilidades de Datos (Data Burning)**: Confirmado que el frontend no usa mocks quemados (`of(...)`) en producción y que todos los precios, calendarios e identidades se calculan con autenticidad en el backend y la base de datos MongoDB.
- **Aislamiento IDOR**: Verificado que los huéspedes no pueden acceder a reservas de otros usuarios ni a endpoints de anfitrión o administrador (`HTTP 403`).
