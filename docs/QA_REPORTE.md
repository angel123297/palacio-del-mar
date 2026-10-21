# 🧪 QA Report — Palacio del Mar

Reporte único de QA del proyecto. Se actualiza **cada día a las 9:00 a.m.** (Colombia) con la tarea programada de Vibe. Aquí se registran los resultados de Jules y de Vibe.

## 📊 Estado actual (verificación local, 18 oct 2026 — 9ª verificación)

- Backend: **256/256 tests** en verde (37 archivos de pruebas, 0 fallos, 0 errores y 0 omitidos).
- Frontend: **13/13 tests** en verde (5 archivos de pruebas en Vitest) y compilación de producción exitosa (`dist/palacio-del-mar-web`).
- CI: `.github/workflows/ci.yml` en verde en GitHub Actions (`plan/marketplace-webhooks`).
- Protocolo `non-breaking-delivery`: estrictamente aplicado y verificado.
- **Inconsistencias Resueltas:** Endpoint `GET /api/host/promotions` agregado e integrado en frontend, soporte dual para cabeceras de API key (`X-API-KEY` y `X-Palacio-Api-Key`), unificación de conteo de tests (256 pruebas en 37 archivos) y normalización de enlaces a `docs/CORRECCIONES.md`.
- Todos los bloqueantes y hallazgos reportados han sido completamente solucionados.

## 🗓️ Reporte diario (bitácora)

### 📅 2026-10-21 — Revisión diaria de Vibe: actividad masiva, PR #20 fusionado y cierre de PRs

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, todos del usuario/agentes):**
- [fix(marketplace): resolver roles de reservas, blindaje de reseñas y depuracion del frontend](https://github.com/angel123297/palacio-del-mar/commit/2870c09df6c7dd73903e938bb903e238e6a2a010) (20 oct, 16:55 UTC)
- [fix(frontend): mejoras integrales por rol (admin, anfitrion y turista), edicion de alojamientos y actualizacion de diagramas](https://github.com/angel123297/palacio-del-mar/commit/f4d508c326a028d9ac600c1cc0a47f7ef33b433f) (20 oct, 23:03 UTC) — 628 adiciones/206 eliminaciones en dashboards, calendario, checkout y diagramas de docs.
- [feat(tests): agregar pruebas unitarias CrearUsuarioUnitTest y ProductoControllerUnitTest y mejoras de accesibilidad WCAG 2.2 AA](https://github.com/angel123297/palacio-del-mar/commit/3f3aeec7857471d6088442423bf8c9ec56a852a7) (21 oct, 01:38 UTC) — nuevos tests backend (`CrearUsuarioUnitTest`, `ProductoControllerUnitTest`), nuevo `accessibility.spec.ts` frontend y atributos ARIA/estilos enfocados a WCAG 2.2 AA.

**Pull requests: sin PRs abiertos. Cambios de estado relevantes:**
- ✅ [#20 — Plan/marketplace webhooks](https://github.com/angel123297/palacio-del-mar/pull/20) fue **FUSIONADO a `proyecto-angular-java`** (rama original) el 21 oct, 01:42 UTC, con head 3f3aeec. El trabajo del marketplace ya está entregado a la rama original.
- 🧹 Se cerraron todos los PRs pendientes: [#22](https://github.com/angel123297/palacio-del-mar/pull/22) (endpoints admin), [#19](https://github.com/angel123297/palacio-del-mar/pull/19), [#21](https://github.com/angel123297/palacio-del-mar/pull/21), [#18](https://github.com/angel123297/palacio-del-mar/pull/18), [#12](https://github.com/angel123297/palacio-del-mar/pull/12), [#4](https://github.com/angel123297/palacio-del-mar/pull/4), [#3](https://github.com/angel123297/palacio-del-mar/pull/3). Backlog de PRs limpio.

**Fase en curso:** Fase 5 (CI/CD, Swagger, Actuator y automaciones) funcionalmente completada. La actividad actual es post-plan: mejoras de rol en frontend (admin/anfitrión/turista), accesibilidad WCAG 2.2 AA y ampliación de cobertura de tests. Con la fusión de #20, el plan de 5 fases queda entregado en la rama original.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados como corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`).

**Tests esperados en verde:** ~263+ de backend (256 verificados el 18 oct + 7 intensivas de b1a284 + los nuevos `CrearUsuarioUnitTest`/`ProductoControllerUnitTest`, conteo exacto por verificar) y ~14+ de frontend (13 verificados + `accessibility.spec.ts`). **Pendiente verificación local/CI.**

**Riesgos / problemas detectados:**
- ⚠️ La fusión de #20 tocó la rama original `proyecto-angular-java`: confirmar que era intencional y que el resultado (3f3aeec + merge) es el estado deseado.
- ⚠️ Los nuevos tests y el conteo total no están verificados en CI por Vibe en esta revisión.
- ⚠️ PR #22 cerrado sin fusión: confirmar si sus endpoints de exportación/aprobación de reservas ya existen en la rama (los mensajes de f4d508c/2870c09 sugieren mejoras de admin relacionadas, pero no está auditado).
- ⚠️ `AGENTS.md` sigue fuera del repositorio (df2c2e0, ignorado vía .gitignore).
- ℹ️ Riesgo anterior de PRs draft acumulados: **resuelto** (todos cerrados).

### 📅 2026-10-20 — Revisión diaria de Vibe: sin cambios en el día

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`):** ninguno nuevo. El único commit reciente es el propio [docs(qa): reporte diario 2026-10-19](https://github.com/angel123297/palacio-del-mar/commit/6fb9b5c6db36796aa80a0531a1f214a8a2f13063) (19 oct, 14:55 UTC), ya fuera de la ventana de esta revisión. **Sin cambios de código ni actividad de agentes (Gemini CLI/Jules/Antigravity) en el día.**

**Pull requests abiertos (sin cambios de estado):**
- [#22 — feat(admin): implement missing bookings export and approve endpoints](https://github.com/angel123297/palacio-del-mar/pull/22) (Jules, base `plan/marketplace-webhooks`, sin actividad desde el 19 oct). Pendiente de revisión y fusión.
- [#20 — Plan/marketplace webhooks](https://github.com/angel123297/palacio-del-mar/pull/20) (`plan/marketplace-webhooks` → `proyecto-angular-java`). **PR de entrega a la rama original; no fusionar sin decisión explícita del usuario.**
- [#19 — Add GET /promotions endpoint for HostController](https://github.com/angel123297/palacio-del-mar/pull/19) (draft). Ya resuelto vía `GET /api/host/promotions` en 876d3ced; candidata a cerrar.
- Drafts con base `proyecto-angular-java` (rama protegida, NO tocar): [#21](https://github.com/angel123297/palacio-del-mar/pull/21), [#18](https://github.com/angel123297/palacio-del-mar/pull/18), [#12](https://github.com/angel123297/palacio-del-mar/pull/12), [#4](https://github.com/angel123297/palacio-del-mar/pull/4), [#3](https://github.com/angel123297/palacio-del-mar/pull/3).

**Fase en curso:** Fase 5 (CI/CD, Swagger, Actuator y automaciones) — funcionalmente implementada; la actividad actual es depuración intensiva y orden del repositorio (post-Fase 5). Sin avance medible hoy por inactividad de agentes.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados como corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`).

**Tests esperados en verde:** 263 de backend (256 verificados el 18 oct + 7 pruebas intensivas de b1a284) y 13 de frontend. **El conteo de 263 sigue pendiente de verificación local/CI** (Vibe no pudo consultar la corrida del workflow en esta revisión; el conector de GitHub no expone las corridas de Actions).

**Riesgos / problemas detectados:**
- ⚠️ Persiste el riesgo del conteo de 263 tests reportado por agente sin verificación en CI.
- ⚠️ PR #22 (endpoints admin de exportación/aprobación de reservas) sigue abierto y sin revisión; puede quedar desactualizado frente a la rama de trabajo.
- ⚠️ `AGENTS.md` sigue removido del repositorio (df2c2e0, ignorado vía .gitignore); si las reglas para agentes siguen siendo requeridas, restaurar fuera del repo o des-ignorar.
- ⚠️ Acumulación de PRs draft sin triaje (#22, #19, #21, #18, #12, #4, #3).
- ⚠️ #20 apunta a `proyecto-angular-java` (rama original del usuario): cualquier fusión requiere aprobación explícita del usuario.

### 📅 2026-10-19 — Revisión diaria de Vibe (depuración intensiva cerrada, 263 tests backend esperados)

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, todos de Gemini CLI del 18–19 oct):**
- [fix: cerrar depuración intensiva de marketplace (concurrencia en confirmIntent, idempotencia de webhooks, sanitización QR/PAN, autenticación CSV, inmutabilidad de Signals, -Xshare:off en Surefire y 7 pruebas intensivas de regresión)](https://github.com/angel123297/palacio-del-mar/commit/b1a2849106faf35fcd6091903ca2f81af9057927)
- [chore: remover archivos y directivas de IA del repositorio y agregar a .gitignore](https://github.com/angel123297/palacio-del-mar/commit/df2c2e01681b2e1ff4b217ff16e9cf1599bd108c)
- [refactor(docs): unificar reportes tecnicos en docs/ y limpiar raiz del repositorio](https://github.com/angel123297/palacio-del-mar/commit/65d9b5ff6757dabbe977404f436065b7bea6932c)
- [refactor(org): organizar DateRangePicker en subdirectorio dedicado y anadir docs/README.md central](https://github.com/angel123297/palacio-del-mar/commit/108d4e47aecc55563149460da18aeeb2afa1482c)
- [fix(qa): resolver inconsistencias de contratos, endpoints de host, cabeceras de integracion y metricas (GET /api/host/promotions anti-IDOR, cabeceras duales X-API-KEY / X-Palacio-Api-Key, 256/256 tests backend en verde)](https://github.com/angel123297/palacio-del-mar/commit/876d3ced0425924944bde4b69294e81eb82a3d2e)

**Pull requests abiertos:**
- [#22 — feat(admin): implement missing bookings export and approve endpoints](https://github.com/angel123297/palacio-del-mar/pull/22) (draft, base `plan/marketplace-webhooks`, actualizado hoy 19 oct). Pendiente de revisión y fusión.
- [#20 — Plan/marketplace webhooks](https://github.com/angel123297/palacio-del-mar/pull/20) (abierto, `plan/marketplace-webhooks` → `proyecto-angular-java`, sincronizado con b1a2849). **PR de entrega a la rama original; no fusionar sin decisión explícita del usuario.**
- [#19 — Add GET /promotions endpoint for HostController](https://github.com/angel123297/palacio-del-mar/pull/19) (draft, base `plan/marketplace-webhooks`). Ya resuelto vía `GET /api/host/promotions` en 876d3ced; candidata a cerrar.
- Drafts con base `proyecto-angular-java` (rama protegida, NO tocar): [#21](https://github.com/angel123297/palacio-del-mar/pull/21), [#18](https://github.com/angel123297/palacio-del-mar/pull/18), [#12](https://github.com/angel123297/palacio-del-mar/pull/12), [#4](https://github.com/angel123297/palacio-del-mar/pull/4), [#3](https://github.com/angel123297/palacio-del-mar/pull/3).

**Fase en curso:** Fase 5 (CI/CD, Swagger, Actuator y automaciones) — funcionalmente implementada; la actividad actual es depuración intensiva y orden del repositorio (post-Fase 5).

**Bugs PDM-01/02/03 y tests @Disabled:** confirmados como corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Hoy b1a284 agrega 7 pruebas intensivas de regresión adicionales.

**Tests esperados en verde:** 263 de backend (256 verificados el 18 oct + 7 nuevas pruebas intensivas de b1a284, conteo nuevo reportado por agente, **pendiente de verificación local/CI**) y 13 de frontend.

**Riesgos / problemas detectados:**
- ⚠️ El conteo de 263 tests proviene del mensaje de commit del agente; verificar la corrida de CI (`.github/workflows/ci.yml`) antes de dar por buena la cifra.
- ⚠️ El commit df2c2e0 **removió `AGENTS.md` y directivas de IA del repositorio** (agregadas a `.gitignore`). Si las reglas para agentes siguen siendo requeridas, deben restaurarse fuera del repo o des-ignorar el archivo.
- ⚠️ Reorganización de docs: `QA_REPORTE.md`, `AUDITORIA_SEGURIDAD.md`, `CORRECCIONES.md` y `PLAN_MARKETPLACE.md` ahora viven en `docs/`; verificar que los enlaces internos y el workflow de la tarea diaria apunten a la nueva ruta.
- ⚠️ Acumulación de PRs draft sin triaje (#22, #19, #21, #18, #12, #4, #3): definir cerrar/re-basificar para no bloquear el flujo.
- ⚠️ #20 apunta a `proyecto-angular-java` (rama original del usuario): cualquier fusión requiere aprobación explícita del usuario.

### 📅 2026-10-18 — Auditoría exhaustiva Vibe post-corrección de inconsistencias (256 tests backend + 13 tests frontend)

**Verificación ejecutada:** backend **256/256** tests (37 archivos de pruebas, 0 fallos, 0 errores, 0 omitidos); frontend build de producción limpio (`dist/palacio-del-mar-web`) y **13/13** tests en Vitest.

**Revisión minuciosa de no-regresión (Vibe Auditor):**

| Componente auditado | Hallazgo verificado | Evaluación de impacto colateral | Veredicto |
|---|---|---|---|
| `HostController.getPromotions` | Endpoint `GET /api/host/promotions` faltante | Valida pertenencia de propiedad (`ownerId`) y sedes asignadas. Si el host no tiene alcance devuelve lista vacía. Cero filtración cross-host. Resuelve la discrepancia de PR #19. | **Aprobado (Sin regresión)** |
| `IntegrationController` | Soporte de cabecera `X-API-KEY` vs `X-Palacio-Api-Key` | Método `authenticate` evalúa de forma encadenada y segura ambos headers y Bearer token sin modificar la lógica criptográfica de lookup por hash. | **Aprobado (Sin regresión)** |
| Frontend Dashboards | Rutas `/host/promotions` y `/admin/promotions` | Los componentes ahora consumen sus respectivos endpoints aislados por rol sin recurrir a rutas globales ni arrojar 404. | **Aprobado (Sin regresión)** |
| Suites de Tests | 5 nuevos tests añadidos (`HostPromotionControllerTest` ampliado y nuevo `IntegrationControllerTest`) | 256 tests ejecutados de forma limpia, cobertura de JaCoCo verificada en 79 clases (37 archivos de prueba). | **Aprobado (100% verde)** |
| Documentación & Enlaces | Desfase en conteos de tests, versiones tecnológicas y enlace a `CORRECCIONES.md` | Normalizados `QA_REPORTE.md`, `AUDITORIA_SEGURIDAD.md`, `AGENTS.md`, `PLAN_MARKETPLACE.md` y `README.md`. | **Aprobado** |

### 📅 2026-10-18 — Revisión matutina de Vibe (previa a correcciones)

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, todos del 17 oct por Gemini CLI; ninguno nuevo hoy 18 oct):**
- [merge: integrar chore/security-audit-vibe en plan/marketplace-webhooks (resiliencia en pagos, anti-SSRF y diagramas)](https://github.com/angel123297/palacio-del-mar/commit/cd8e9921efa7fbca5532edf882e469f38d5b77f0)
- [feat(security-docs): cierre integral PDM-04/07/08, unificación HostController con propertyId, playbook E2E y diagramas](https://github.com/angel123297/palacio-del-mar/commit/842442825713d99ba851a3d5b25ae90de105fa14)
- [fix(qa): resolver 5 hallazgos de calidad y adoptar protocolo non-breaking-delivery](https://github.com/angel123297/palacio-del-mar/commit/5c1981dd46b2d931e784e40f43fab424354cf750)
- [fix(marketplace): resolución de hallazgos residuales de Copilot (reembolso automático, filtros públicos, caché y ocupación multi-unidad)](https://github.com/angel123297/palacio-del-mar/commit/1b246dc4b58f6433125da43d9322d841152785e3)
- [fix(core): resolver bean PaymentWebhookService, filtrar ocupación admin por APPROVED y cola de reembolsos](https://github.com/angel123297/palacio-del-mar/commit/85b3e705fb838775912ce3ae5c1674130b7cdfd8)
- [fix(admin): validar respuesta de pasarela en resolveRefund](https://github.com/angel123297/palacio-del-mar/commit/716fa459e81eab38b86728770574b8c7affb4ff3)
- [chore(docker): .dockerignore en frontend, backend y root](https://github.com/angel123297/palacio-del-mar/commit/e82b38bd704f0bb6590bf6bebcbc3c3c666ce3e2)
- [fix(catalog): evict de caché Redis de suites en mutaciones de propiedades y habitaciones](https://github.com/angel123297/palacio-del-mar/commit/6283e8e7918cfbdbcba64cc0bafb1a8f2102285f)
- [fix(core): 5 edge cases de deep-debug en bookings, PMS unblock, refunds, payouts y zona horaria Bogotá](https://github.com/angel123297/palacio-del-mar/commit/3c87d80ad1d7a12d204ad3fcc637e12e3349a98f)
- [feat(security): blacklist JWT multi-tier durable, invalidación de sesión al cambiar contraseña y Actuator restringido](https://github.com/angel123297/palacio-del-mar/commit/e91f1eacb62c7e11e0d508a526ad0cbcf2808b44)
- [feat(email): notificaciones transaccionales con Mailpit, vouchers PDF/ICS y alertas de anfitrión](https://github.com/angel123297/palacio-del-mar/commit/a1f64e209764cd8743715ae04d56e6f86029715e)
- [feat(ical): feeds RFC 5545 para propiedades y suites con sync Airbnb/Booking y anti-IDOR](https://github.com/angel123297/palacio-del-mar/commit/10193ec2889944685305df6b5274530b71e68410)
- [feat(voucher): comprobante imprimible con QR offline, PIN inteligente y calificación verificada](https://github.com/angel123297/palacio-del-mar/commit/33da3c55cb95c6ebfff785b7f7edebbdc888f7c0)
- [docs: playbook de sustentación, métricas de QA y remoción de advertencias de deprecación](https://github.com/angel123297/palacio-del-mar/commit/c7be8e7867671e6cc931d3142ba48aadb817d3e1)

**Pull requests abiertos:**
- [#19 — Add GET /promotions endpoint for HostController](https://github.com/angel123297/palacio-del-mar/pull/19) (draft, base `plan/marketplace-webhooks`, actualizado hoy 18 oct). Resuelto en el commit actual mediante `GET /api/host/promotions`.
- [#18 — Remove mock Loyalty feature](https://github.com/angel123297/palacio-del-mar/pull/18) (draft, base `proyecto-angular-java` — rama protegida, NO tocar).
- Drafts antiguos sin actividad con base `proyecto-angular-java`: [#12](https://github.com/angel123297/palacio-del-mar/pull/12), [#4](https://github.com/angel123297/palacio-del-mar/pull/4), [#3](https://github.com/angel123297/palacio-del-mar/pull/3) — sin cambios, base protegida.

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
- ✅ `docs/CORRECCIONES.md` normalizado y referenciado correctamente bajo `docs/`.
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
- `docs/CORRECCIONES.md` — bitácora de correcciones
- `PLAN_MARKETPLACE.md` — plan aprobado (5 fases)
- `AGENTS.md` — reglas para agentes de IA
"}}]