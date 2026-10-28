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
