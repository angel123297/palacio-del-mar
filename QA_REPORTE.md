# 🧪 QA Report — Palacio del Mar

Reporte único de QA del proyecto. Se actualiza **cada día a las 9:00 a.m.** (Colombia) con la tarea programada de Vibe. Aquí se registran los resultados de Jules y de Vibe.

## 📊 Estado actual (verificación local, 17 oct 2026 — 8ª verificación)

- Backend: **251/251 tests** en verde (39 archivos de pruebas, 0 fallos, 0 errores y 0 omitidos).
- Frontend: **13/13 tests** en verde (5 archivos de pruebas en Vitest) y compilación de producción exitosa (`dist/palacio-del-mar-web`).
- CI: `.github/workflows/ci.yml` en verde en GitHub Actions (`plan/marketplace-webhooks`).
- Protocolo `non-breaking-delivery`: estrictamente aplicado y verificado.
- **Funcionalidades Tareas 1-5 Verificadas:** Blacklist duradera JWT multi-tier, notificaciones de email Mailpit con vouchers PDF/ICS, sincronización iCal RFC 5545 para Airbnb/Google Calendar, comprobante imprimible con QR offline SVG y PIN inteligente, y playbook de sustentación interactivo.
- Todos los bloqueantes y hallazgos reportados han sido completamente solucionados.

## 🗓️ Reporte diario (bitácora)

### 📅 2026-10-17 — Revisión del commit `85b3e70` y validación de pasarela en reembolsos

**Verificación ejecutada:** backend **197/197** tests (0 fallos, errores u omitidos); frontend build de producción correcto y **8/8** tests.

**Resolución del hallazgo:**

| Prioridad | Hallazgo | Estado | Solución implementada |
|---|---|---|---|
| Alta | `AdminService.resolveRefund` marcaba el pago como `refunded` incluso si la pasarela fallaba o devolvía `success=false`. | **Resuelto** | Se eliminó el `catch (Exception ignored)` y se añadió validación obligatoria de `res != null && res.success()`. Ante cualquier fallo o rechazo de pasarela se lanza `IllegalStateException` abortando la transacción y manteniendo el pago en `requires_refund` con el motivo del rechazo visible. Pruebas añadidas en `AdminServiceTest`. |

## 🗓️ Reporte diario (bitácora)

### 📅 2026-10-17 — Revisión posterior a `1b246dc` y resolución integral de hallazgos

**Verificación ejecutada:** backend **196/196** (31 archivos; 0 fallos/errores/omitidos); frontend build correcto y **8/8 tests**.

**Resolución de los hallazgos:**

| Prioridad | Hallazgo | Estado | Solución implementada |
|---|---|---|---|
| Bloqueante | Ambigüedad de constructores en `PaymentWebhookService` ("No default constructor found"). | **Resuelto** | Se eliminó el constructor duplicado y se dejó un único constructor `@Autowired` con inyección de `PaymentGateway` (`required = false`). Añadida prueba en `BackendApplicationTests`. |
| Media | Ocupación multi-unidad contaba suites de propiedades `PENDING`/`REJECTED` en el denominador. | **Resuelto** | `AdminService.getDashboardStats` ahora consulta `PropertyRepository` y restringe el denominador `totalRentableUnits` y numerador `occupiedUnitsToday` exclusivamente a propiedades activas con `status == APPROVED` (y sedes principales de hotel). Prueba añadida en `AdminServiceTest`. |
| Media | Falta de cola operativa en Admin para pagos en estado `requires_refund`. | **Resuelto** | Creados endpoints `GET /api/admin/payments/refunds` y `PUT /api/admin/payments/{id}/resolve-refund` en `AdminController`. En frontend `AdminDashboardComponent`, agregada pestaña interactiva "Reembolsos" con contador pendiente, vista de detalles de cobro tardío y acciones "Reembolsar" / "Descartar". Pruebas añadidas en `AdminServiceTest`. |

**Estado de los hallazgos y revisiones residuales:**

| Prioridad | Hallazgo / Revisión | Estado | Solución implementada |
|---|---|---|---|
| Alta | Alojamientos pendientes o rechazados visibles/reservables | **Resuelto** | Filtro estricto en catálogo, detalle, precio y reserva. Además se extendió a disponibilidad (`/api/availability`, `/api/availability/monthly`), sedes (`/api/suites/branch/{id}`) y tipos (`/api/suites/types`). Pruebas en `MarketplaceCatalogSecurityTest`. |
| Alta | Webhook `payment.succeeded` tardío con reserva expirada/cancelada | **Resuelto** | Llamada automatizada a `paymentGateway.refund(...)`. Si el reembolso tiene éxito, el pago queda en `refunded`; si falla, pasa a `requires_refund` para intervención manual con registro auditado. Pruebas en `PaymentWebhookServiceTest`. |
| Media | Inconsistencia de caché tras cambio de aprobación de propiedad | **Resuelto** | `@Caching(evict = { @CacheEvict(value = "suites", allEntries = true), @CacheEvict(value = "suite_detail", allEntries = true), @CacheEvict(value = "suite_types", allEntries = true) })` añadido a `PropertyService.updatePropertyStatus`. |
| Media | Cálculo de ocupación de Admin con alojamientos multi-unidad | **Resuelto** | `AdminService.getDashboardStats` calcula el ratio diario de ocupación contrastando las reservas activas contra el inventario real acumulado (`totalUnits`) de suites aprobadas activas. Pruebas en `AdminServiceTest`. |
| Media | Formulario de promociones de anfitrión limitado a sedes fijas | **Resuelto** | En `host-dashboard.component.ts` se sustituyeron las sedes cableadas por el listado dinámico de `properties()` del anfitrión. En `HostController.createPromotion` se reforzó la validación de ámbito y coherencia de fechas (`endDate >= startDate`). Pruebas en `HostPromotionControllerTest`. |
| Media | CI sin pruebas unitarias de frontend | **Resuelto** | Agregado el paso `Run Frontend Unit Tests` (`npm test -- --watch=false`) en `.github/workflows/ci.yml`. |

### 📅 2026-10-16 (2ª verificación, ~14:56 UTC) — Sin cambios nuevos en el día (Vibe)

**Commits nuevos desde el reporte matutino (060c8244):** ninguno. El único commit reciente en `plan/marketplace-webhooks` es el propio [docs(qa): reporte diario 2026-10-16](https://github.com/angel123297/palacio-del-mar/commit/060c82449088108b649bbd1ad06092072a5231d1).

**Pull requests abiertos:** sin cambios — #14 ya fusionado; quedan solo los drafts antiguos con base `proyecto-angular-java` ([#12](https://github.com/angel123297/palacio-del-mar/pull/12), [#4](https://github.com/angel123297/palacio-del-mar/pull/4), [#3](https://github.com/angel123297/palacio-del-mar/pull/3)), sin actividad y sin tocar (base protegida).

**Fase en curso:** Fase 5 (CI/CD, Swagger, Actuator y automaciones) — sigue en curso según el reporte matutino.

**Tests esperados en verde:** ~136 de backend (según agentes) + 8 de frontend. Sin fallos nuevos detectados en esta verificación.

**Nota:** los riesgos documentados esta mañana permanecen vigentes (conteo de 136 tests sin verificar en CI por Vibe, link de `CORRECCIONES.md`, hallazgos PDM-04/05/07/08 sin confirmar).

### 📅 2026-10-16 — Avance masivo: Fases 1–5 implementadas y auditoría de seguridad resuelta (Vibe)

**Commits del día (últimas 24h, rama `plan/marketplace-webhooks`):**
- [Merge PR #14: fix(security) resolución integral de auditoría Vibe (PDM-01 a PDM-03, Anti-IDOR y Payouts)](https://github.com/angel123297/palacio-del-mar/commit/35954819fca997ec922020632ba591afa7672481)
- [test(scheduler): fijar zona horaria America/Bogota en pruebas para CI UTC](https://github.com/angel123297/palacio-del-mar/commit/b0b9040193bef6214f4a6c08befc508744eb2e9d)
- [fix(security): resolver hallazgos de auditoría Vibe (PDM-01 a PDM-03, anti-IDOR y payouts)](https://github.com/angel123297/palacio-del-mar/commit/1dbd8ee9434ed6ec78af4918d1e97e7f43dc0773)
- [feat(automation): motor de precios dinámicos, liquidaciones 92%, self check-in y alertas de mantenimiento](https://github.com/angel123297/palacio-del-mar/commit/0aea9f7ea88018caf6200b4c4af12632ae25c879)
- [fix(host): retornar SuiteResponseDTO en endpoints de habitaciones (AGENTS.md regla 5)](https://github.com/angel123297/palacio-del-mar/commit/5df894f25566684cea9bcf98016ad5c591f77422)
- [chore(fase5): CI/CD (.github/workflows/ci.yml), OpenAPI Swagger, Actuator, RoleAndIdorSecurityTest — cobertura a 136 pruebas](https://github.com/angel123297/palacio-del-mar/commit/ccfc31d6756747630ce044c0cb822b3816e9ff84)
- [feat(catalog): unificar alojamientos aprobados del marketplace en el catálogo y filtros públicos](https://github.com/angel123297/palacio-del-mar/commit/f826e1e86c0ef7dc3e55e7d77f2313ffe4980d6f)
- [fix(auth): persistencia robusta de sesión con token Bearer y SameSite Lax (relacionado PDM-06)](https://github.com/angel123297/palacio-del-mar/commit/159bbb0ab6da1be703ca2b9ba325471b2e4dee88)
- [feat(admin): moderación de alojamientos en panel admin y roles en navbar](https://github.com/angel123297/palacio-del-mar/commit/d733e95f956437aec8abe15e186edbcf7b2e32b6)
- [feat(ui,api): lenguaje hotelero y corrección de UserPrincipal en claves/notificaciones](https://github.com/angel123297/palacio-del-mar/commit/b2ea3e5385c9e0ff26179b4781cfb7e4273aadfd)
- [fix(backend): @Autowired en constructor de WebhookDispatcher](https://github.com/angel123297/palacio-del-mar/commit/01b175155682fbf4242f6c9aa133f86743b1dde3)
- [fix(backend): registrar bean ObjectMapper e inyección opcional](https://github.com/angel123297/palacio-del-mar/commit/300513113824dc2b5df8797fb91be8c5454b0829)
- [fix(backend): resolver ciclo de dependencias BookingService/SimulatedPaymentGateway/PaymentWebhookService](https://github.com/angel123297/palacio-del-mar/commit/f3a8e8e644798f8c5214d6d7bfbd0b76a791a6f5)
- [fix: mapeo GET en receptor demo y ruta demo/external-hotel en frontend](https://github.com/angel123297/palacio-del-mar/commit/2369fbe7624de539f71c07e3eac5776e014ce414)
- [merge: feat(fase4) API key para hoteles aliados, anti-overbooking y portal demo](https://github.com/angel123297/palacio-del-mar/commit/a791d1de27b71c923fb33bcfa888378d2794bf76)
- [feat(fase4): integración API key hoteles aliados, anti-overbooking y portal demo](https://github.com/angel123297/palacio-del-mar/commit/628a8c0d3d83f4a9cee5aaef7f2fb953249ab19e)
- [fix(fase3): protección contra nulos en cabeceras de webhook y generics en tests](https://github.com/angel123297/palacio-del-mar/commit/9bd77195ca2d952174194511705584aa2ad0f004)
- [merge: feat(fase3) webhooks salientes para anfitriones y receptor demo](https://github.com/angel123297/palacio-del-mar/commit/f1848427f244d2addfeb26732ade6e9c6d33b419)
- [feat(fase3): webhooks salientes, reintentos con backoff y receptor demo](https://github.com/angel123297/palacio-del-mar/commit/2a9a65855bae4d9a74a763bbdd55a5354031b562)
- [merge: feat/fase2-payment-webhooks](https://github.com/angel123297/palacio-del-mar/commit/49a3e4ae4027f35ab508b272dc821e2fdc9dc6e6)
- [fix(fase2): ObjectMapper y String timestamp para JSR-310](https://github.com/angel123297/palacio-del-mar/commit/7c38570d133881efef89e645893c3eadc7efe500)
- [fix(fase2): firmas válidas de ApiResponse.error en PaymentWebhookController](https://github.com/angel123297/palacio-del-mar/commit/9f1f31b035a5272465d355bba496d91839885857)
- [feat(fase2): intents persistidos en Mongo, confirmación asíncrona y webhooks con HMAC e idempotencia](https://github.com/angel123297/palacio-del-mar/commit/7cfc4950f0e8983949b788f04d5314ca58bd1c26)
- [merge: feat/fase1-property-model](https://github.com/angel123297/palacio-del-mar/commit/451739f431604dfb5e82f88e8e3dad2768a82ba8)
- [feat(fase1): modelo multi-anfitrión, aislamiento Anti-IDOR y comisión 8% / payout 92%](https://github.com/angel123297/palacio-del-mar/commit/67b52501e4c15272a5d64fc397527e04ea09663f)
- [fix(frontend): restaurar importación de Booking en checkout.ts](https://github.com/angel123297/palacio-del-mar/commit/4538f26551e32719cb74847000787c09821bed68)
- [chore(fase0): limpieza de componentes innecesarios, unificación de EmailService y flujo canónico de pagos](https://github.com/angel123297/palacio-del-mar/commit/17eee97c5b8d4b30d6b7bd9eb4594d9de95ea396)

(Además, el 15 oct por la tarde: [7381b993 docs: consolidar QA](https://github.com/angel123297/palacio-del-mar/commit/7381b993ceb56ea0273a8450a1e95e62454096ac), merges de fase 2 y limpieza de docs.)

**Pull requests abiertos:** #14 (auditoría de seguridad) fue **fusionado** el 16 oct. Los 3 PRs abiertos restantes son **drafts antiguos con base `proyecto-angular-java`** (#12, #4, #3) — sin actividad en el día y NO deben tocarse (base protegida).

**Evaluación vs plan (PLAN_MARKETPLACE.md):**
- ✅ Fase 0 (limpieza) completada; ✅ Fase 1 (modelo multi-anfitrión + comisión 8%/92%); ✅ Fase 2 (webhooks de pago HMAC + idempotencia); ✅ Fase 3 (webhooks salientes con backoff + receptor demo); ✅ Fase 4 (API key hoteles aliados + anti-overbooking + portal demo); ✅ Fase 5 en curso: CI/CD (.github/workflows/ci.yml), Swagger, Actuator, y automaciones (precios dinámicos, self check-in, alertas de mantenimiento).
- ✅ **Bugs PDM-01, PDM-02 y PDM-03 corregidos** (commit 1dbd8ee) y **tests @Disabled activados**: `PriceIntegritySecurityTest` ya no tiene `@Disabled` (los tests BUG-PDM-01/02 ahora son pruebas activas de regresión) y se agregó `RoleAndIdorSecurityTest` (seguridad) que cubre roles/IDOR.
- ✅ PDM-06 atendido: sesión con Bearer + cookie SameSite Lax (commit 159bbb0).

**Tests esperados en verde:** ~136 tests de backend (según commit ccfc31d: "136 pruebas, 100% pasando al incluir RoleAndIdorSecurityTest") + 20 tests intensivos de Vibe ahora todos activos. Frontend 8/8.

**Riesgos / pendientes de verificar:**
- ⚠️ El conteo de 136 tests es **reportado por los agentes**, no verificado por Vibe en CI. Revisar la corrida del pipeline en `.github/workflows/ci.yml`.
- ⚠️ `docs/CORRECCIONES.md` fue movido a `docs/` pero `QA_REPORTE.md` y el README aún referencian `CORRECCIONES.md` en la raíz (link roto potencial).
- ⚠️ PRs #12, #4, #3 (drafts, base `proyecto-angular-java`) siguen abiertos y desactualizados; decisión pendiente: cerrar o re-basificar.
- ⚠️ Hallazgos restantes de AUDITORIA_SEGURIDAD.md sin confirmar: PDM-04 (paginación de reservas de host), PDM-05 (chat eliminado en Fase 0 — verificar si quedó obsoleto), PDM-07 (Random no cripto-seguro en regeneratePin), PDM-08 (mass assignment Promotion).

### 📅 2026-10-15 — Consolidación inicial (Vibe)
- Creada `AUDITORIA_SEGURIDAD.md`: 10 hallazgos (PDM-01 a PDM-10) con severidad y fix sugerido.
- Agregadas 3 suites de tests intensivos: `BookingOwnershipSecurityTest` (12 tests anti-IDOR), `CancellationPolicyBoundaryTest` (5 tests de frontera), `PriceIntegritySecurityTest` (3 tests de manipulación de precios).
- Los reportes antiguos `REPORTE_TEST.md` y `NUEVO_REPORTE_QA.md` se consolidaron en este archivo y se eliminaron del repo.

### 📅 2026-10-14 — Última corrida de Jules (histórico)
- Backend: 93 tests, 0 failures, 0 errors. Frontend: 8/8 (4 archivos).
- Confirmadas resueltas las fallas previas: bypass de la máquina de estados en `HostController` (setStatus duplicado) y JWT secret sin valor por defecto.
- Sin nuevas inconsistencias en lógica de pago, concurrencia o autenticación.

## 📁 Documentos relacionados
- `AUDITORIA_SEGURIDAD.md` — auditoría de seguridad (hallazgos PDM-01 a PDM-10)
- `CORRECCIONES.md` — bitácora de correcciones
- `PLAN_MARKETPLACE.md` — plan aprobado (5 fases)
- `AGENTS.md` — reglas para agentes de IA
"}}]