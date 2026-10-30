## 🗓️ Reporte diario (bitácora)

### 📅 2026-10-30 — Revisión diaria de Vibe: sin cambios de código en el día

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`):** **sin cambios de código en el día.** El único commit en la ventana es el propio reporte de QA de ayer: [docs(qa): reporte diario 2026-10-29](https://github.com/angel123297/palacio-del-mar/commit/c705eb052520a275aa057dd5f832a674e2513d65) (29 oct 14:12 UTC). Ningún commit nuevo de agentes (Antigravity/Jules) ni actividad funcional.

**Pull requests: 2 PRs abiertos, sin cambios desde ayer:**
- [#25 — Add System Audit Report](https://github.com/angel123297/palacio-del-mar/pull/25) (draft, base `plan/marketplace-webhooks`, sin actividad desde el 29 oct 12:19 UTC). Pendiente de decisión: mover `REPORT.md` a `docs/` o cerrar antes de fusionar.
- [#23 — Cleanup unused frontend code and dependencies](https://github.com/angel123297/palacio-del-mar/pull/23) (draft, sin actividad desde el 26 oct). ⚠️ Base `proyecto-angular-java` (rama original protegida): NO fusionar sin decisión explícita del usuario.

**Fase en curso:** sin avance post-plan. Fases 0-4 del PLAN_MARKETPLACE.md completadas (plan fusionado vía #20 el 21 oct); Fase 5 (CI/CD, Swagger, Actuator y automaciones) funcionalmente implementada pero sin commits nuevos. Última actividad funcional: remediación de auditoría del 28 oct (2ad52bc + 0c9df4c).

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de la auditoría de seguridad permanecen cerrados.

**Tests esperados en verde:** **446 de backend y 43 de frontend** (cifras heredadas del reporte de PR #25 y README; sin commits de código no hay riesgo de regresión nueva). ⚠️ Cifra reportada por agente, no verificada por Vibe en CI sobre el HEAD actual (c705eb0).

**Riesgos / problemas detectados:**
- ⚠️ Estancamiento: dos días consecutivos sin commits funcionales de los agentes (Antigravity/Jules); la Fase 5 no registra avance.
- ⚠️ Cifras de tests (446/43) siguen sin verificación local/CI por Vibe.
- ⚠️ PR #25 (`REPORT.md` en la raíz, duplica `docs/QA_REPORTE.md`) y PR #23 (base protegida) pendientes de decisión del usuario.
- ⚠️ Docs eliminados por 0ff7a9e (`docs/PLAN_MARKETPLACE.md`, `docs/AUDITORIA_SEGURIDAD.md`, `docs/CORRECCIONES.md`, etc.) siguen sin restaurarse (recuperables del commit fd81791).
- ⚠️ Commit 4ddecea ("Actualización de credencial de Gmail"): sigue pendiente verificar secretos en historial y rotar la credencial si se expuso.

### 📅 2026-10-29 — Revisión diaria de Vibe: sin cambios de código en el día; nuevo PR draft #25 (reporte de auditoría)

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`):** **sin cambios de código en el día.** Los únicos commits dentro de la ventana ya estaban documentados en el reporte del 28 oct: [fix(config): constructor explicito en DataInitializer](https://github.com/angel123297/palacio-del-mar/commit/0c9df4cf37dde6e74f85cf1b0a434fa95197e2a3) (28 oct 14:32 UTC) y el propio reporte de QA ([56a9938](https://github.com/angel123297/palacio-del-mar/commit/56a99384024db6fe71ba9fdc6c4c046d140c4d9a), 28 oct 14:44 UTC). Ningún commit nuevo de agentes (Antigravity/Jules) en el día.

**Pull requests: 2 PRs abiertos:**
- 🆕 [#25 — Add System Audit Report](https://github.com/angel123297/palacio-del-mar/pull/25) (draft, base `plan/marketplace-webhooks`, head `audit-report-10494833610902290366`, actualizado hoy 12:19 UTC). Solo agrega `REPORT.md` en la raíz (+41 líneas): reporte de un agente que afirma **446 tests backend y 43 frontend en verde (0 fallos, 0 omitidos)**, verifica endpoints críticos autenticados y `jwt.secret=${JWT_SECRET}`. Cifras auto-reportadas, no verificadas por Vibe en CI. ⚠️ Sugiere mover `REPORT.md` a `docs/` antes de fusionar (la convención del repo centraliza reportes en `docs/`).
- [#23 — Cleanup unused frontend code and dependencies](https://github.com/angel123297/palacio-del-mar/pull/23) (draft, sin actividad desde el 26 oct). ⚠️ Su base es `proyecto-angular-java` (rama original protegida): NO fusionar sin decisión explícita del usuario.

**Fase en curso:** plan de 5 fases **entregado** (fusionado vía #20 el 21 oct). Sin avance post-plan en el día: la última actividad funcional sigue siendo la remediación de auditoría del 28 oct (2ad52bc + 0c9df4c).

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** **446 de backend y 43 de frontend** (cifras del reporte de #25 y README, heredadas del 28 oct; sin commits de código hoy no hay riesgo de regresión nueva). **Cifra reportada por agente, no verificada por Vibe en CI**; pendiente verificar la corrida de GitHub Actions sobre el HEAD actual (0c9df4c).

**Riesgos / problemas detectados:**
- ⚠️ `REPORT.md` de PR #25 en la raíz del repo: duplica información de `docs/QA_REPORTE.md` y rompe la convención `docs/`; revisar antes de fusionar.
- ⚠️ Cifras de tests (446/43) aún sin verificación local/CI por Vibe sobre 0c9df4c.
- ⚠️ PR #23 con base en `proyecto-angular-java` (rama original protegida): NO fusionar sin decisión explícita; considerar cerrar o cambiar la base.
- ⚠️ Docs eliminados por 0ff7a9e (`PLAN_MARKETPLACE.md`, `AUDITORIA_SEGURIDAD.md`, `CORRECCIONES.md`, `DEMO_SCRIPT.md`, `README.md` bajo `docs/`) siguen sin restaurarse (recuperables del commit fd81791).
- ⚠️ Commit 4ddecea ("Actualización de credencial de Gmail"): sigue pendiente verificar secretos en historial y rotar la credencial si se expuso.
- ⚠️ `AGENTS.md` sigue fuera del repositorio (ignorado vía .gitignore).
- ⚠️ La app móvil (respaldada en `archive/mobile-flutter-v1`) sigue sin suite de tests propia.

### 📅 2026-10-28 — Revisión diaria de Vibe: remediaciones de seguridad masivas, merge a producción y ⚠️ eliminación de docs de QA/auditoría (restaurado este reporte)

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, 27–28 oct):**
- [docs(qa): reporte diario 2026-10-27](https://github.com/angel123297/palacio-del-mar/commit/fd81791966fbfe9f8b59b96c8e79e4c2d06be442) (27 oct 14:31 UTC, Vibe).
- [fix(marketplace): sanitize backend and frontend, remove duplicate packages, and restore security hardening](https://github.com/angel123297/palacio-del-mar/commit/0ff7a9e6ce2d5aedaf4de13ab3c15186b0b3ee1a) (27 oct 16:34 UTC) — sanitización integral, paquete Java duplicado eliminado, `GeoController`/DTOs nuevos, schedulers robustecidos. ⚠️ **Este commit ELIMINÓ `docs/QA_REPORTE.md`, `docs/PLAN_MARKETPLACE.md`, `docs/AUDITORIA_SEGURIDAD.md`, `docs/CORRECCIONES.md`, `docs/DEMO_SCRIPT.md` y `docs/README.md`** (ver riesgos; este reporte se restaura con esta revisión).
- [Mapa Leaflet en la home entre ubicaciones y suites](https://github.com/angel123297/palacio-del-mar/commit/692d61990d314566980daf6fdab69f298fa48b8e) (S4mu0, 27 oct 19:18 UTC).
- [test(controllers): add MockMvc coverage for auth, availability, booking, payment, and property endpoints](https://github.com/angel123297/palacio-del-mar/commit/8cebe614c9e15d4cb60c632fe49dbb662e750751) (27 oct 19:21 UTC) — nueva cobertura MockMvc.
- [refactor(mobile): desacoplar mobile-flutter respaldado en archive/mobile-flutter-v1](https://github.com/angel123297/palacio-del-mar/commit/d88bd31e885098ceecc5e729fddc19fa1c5f96d8) (27 oct 21:11 UTC).
- [fix(docker): resolver salida de red en backend y añadir alias de red en compose](https://github.com/angel123297/palacio-del-mar/commit/aed5694dbd84343653388c30ad4b071188d18f93) (28 oct 01:38 UTC).
- [fix(security): manejar 403 tipado con ForbiddenAccessException y restringir host demo en webhooks](https://github.com/angel123297/palacio-del-mar/commit/ad7a13a0ae84d1089b409be8dbd20c5c78c111f1) (28 oct 01:38 UTC).
- [fix(booking): preservar titular al modificar fechas y asegurar consistencia de disponibilidad y pagos](https://github.com/angel123297/palacio-del-mar/commit/23e08fc49b230d73e872621609e721a87789df83) (28 oct 01:38 UTC).
- [feat(auth,routing): preservar returnUrl en expiración de sesion y estabilizar scroll en navbar](https://github.com/angel123297/palacio-del-mar/commit/a06cd168d62e71f3224095bae600802a4fddfea0) (28 oct 01:39 UTC).
- [feat(views): modal de cambio de fechas, refinamiento en busqueda y resenas en suites](https://github.com/angel123297/palacio-del-mar/commit/a3836ea75b3af4265217078658eea4763c82fd8c) (28 oct 01:39 UTC).
- [style(css): remover clases huerfanas, estandarizar tipografia wcag y anadir sitemap seo](https://github.com/angel123297/palacio-del-mar/commit/03cea2a7299ce0c06f478f1411b15c324db687a0) (28 oct 01:40 UTC).
- [fix(reviews): exigir checkout completado para calificar, tipar 403 y habilitar liquidacion manual admin](https://github.com/angel123297/palacio-del-mar/commit/6534fe6f8b4e56b2eb94d51da283fe2a1f844474) (28 oct 01:48 UTC).
- [Merge plan/marketplace-webhooks into proyecto-angular-java (produccion)](https://github.com/angel123297/palacio-del-mar/commit/83dfd038b1334bbfef23377b58035233b558f548) (28 oct 02:35 UTC) — ⚠️ **nueva fusión sobre la rama original `proyecto-angular-java`** (reporta 444 tests backend / 43 frontend y build de producción exitoso).
- [fix(security): silenciar advertencia de InitializeUserDetailsBeanManagerConfigurer en arranque](https://github.com/angel123297/palacio-del-mar/commit/f160764b9593d8a2d9409b395712e39dc9114610) (28 oct 02:41 UTC) y [refactor(security): resolver advertencia mediante ProviderManager explicito](https://github.com/angel123297/palacio-del-mar/commit/acad80bbfb6a319ee45ff4109ebdf374441eccba) (28 oct 13:17 UTC).
- [fix(security): aplicar remediaciones de auditoria completa en backend y frontend](https://github.com/angel123297/palacio-del-mar/commit/2ad52bc7602b173aa6df3b8fed4d0f199e58360d) (28 oct 14:09 UTC) — DataInitializer tras `SEED_DEMO_DATA`, reseñas no verificadas `approved=false`, contraseñas unificadas ≥8 caracteres, 404 en vez de 500 (`NoResourceFoundException`), política de cancelación alineada con RN-F03 (<48h retención 100%), WCAG en host-dashboard, `redis:8-alpine`, `npm ci` reproducible, README con cifras 446 backend / 43 frontend.
- [fix(config): constructor explicito en DataInitializer](https://github.com/angel123297/palacio-del-mar/commit/0c9df4cf37dde6e74f85cf1b0a434fa95197e2a3) (28 oct 14:32 UTC) — HEAD actual de la rama.

**Pull requests:** sigue abierto **1 PR**: [#23 — Cleanup unused frontend code and dependencies](https://github.com/angel123297/palacio-del-mar/pull/23) (draft, sin actividad desde el 26 oct). ⚠️ Su base es `proyecto-angular-java` (rama original protegida): NO fusionar sin decisión explícita del usuario.

**Fase en curso:** plan de 5 fases **entregado** (fusionado vía #20 el 21 oct). La actividad del día es post-plan: remediación de hallazgos de auditoría, robustez de arranque (ProviderManager), seguridad de reseñas/contraseñas, WCAG/SEO, Docker y merge de producción.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). El commit 2ad52bc aplica además remediaciones adicionales de auditoría (reseñas `approved=false` para moderación, contraseñas ≥8, 403/404 tipados, credenciales demo tras flag `SEED_DEMO_DATA`).

**Tests esperados en verde:** **446 de backend y 43 de frontend** según el mensaje de 2ad52bc y README (444 reportados en el merge 83dfd03 + los del día). **Cifra reportada por el agente, no verificada por Vibe en CI**; los commits tocan BookingService, ReviewService, AuthController, schedulers y webhooks, todos cubiertos por suites existentes.

**Riesgos / problemas detectados:**
- 🚨 **El commit 0ff7a9e eliminó los documentos clave del repo** (`docs/QA_REPORTE.md`, `docs/PLAN_MARKETPLACE.md`, `docs/AUDITORIA_SEGURIDAD.md`, `docs/CORRECCIONES.md`, `docs/DEMO_SCRIPT.md`, `docs/README.md`). Este reporte se **restaura** con esta revisión; decidir si restaurar también el resto (recuperables del commit fd81791).
- ⚠️ El merge 83dfd03 volvió a modificar la rama original `proyecto-angular-java`: confirmar que fue intencional (regla: rama original no debe tocarse).
- ⚠️ PR #23 con base en `proyecto-angular-java`: NO fusionar sin decisión explícita.
- ⚠️ Conteo de tests (446/43) sin verificación local/CI por Vibe; verificar corrida de GitHub Actions sobre el HEAD actual (0c9df4c).
- ⚠️ `DataInitializer` con `ADMIN_DEFAULT_PASSWORD` parametrizada: confirmar que en producción exige valor seguro (sin fallback débil).
- ⚠️ Commit 4ddecea ("Actualización de credencial de Gmail", 26 oct): sigue pendiente verificar secretos en historial y rotar la credencial si se expuso.
- ⚠️ La app móvil (respaldada en `archive/mobile-flutter-v1`) sigue sin suite de tests propia.


### 📅 2026-10-27 — Revisión diaria de Vibe: sin cambios en el día

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, 26–27 oct):** solo el propio reporte de QA — [docs(qa): reporte diario 2026-10-26](https://github.com/angel123297/palacio-del-mar/commit/e6c9992db8d2ec153fbf83bd83262b70ccd73233) (26 oct 14:13 UTC, Vibe). **Sin cambios de código nuevos en el día.**

**Pull requests:** sigue abierto **1 PR**: [#23 — Cleanup unused frontend code and dependencies](https://github.com/angel123297/palacio-del-mar/pull/23) (draft, creado 26 oct 04:11 UTC, sin actualizaciones desde entonces). ⚠️ Su base es `proyecto-angular-java` (rama original protegida): NO fusionar sin decisión explícita del usuario; revisar y decidir cerrar o cambiar la base.

**Fase en curso:** plan de 5 fases **entregado** (fusionado el 21 oct). El trabajo reciente es post-plan: app móvil (fases 1–4 móviles), auth (recuperación de contraseña, refresh tokens), historial/cancelación, mapa Leaflet y correos HTML. Hoy no hubo avance en ninguno de esos frentes.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** ~263+ de backend y ~14+ de frontend (línea base del 22 oct; sin commits nuevos de código, no hay riesgo de regresión nueva, pero el conteo sigue sin verificación local/CI por Vibe en esta ventana).

**Riesgos / problemas detectados (sin cambios, siguen abiertos):**
- ⚠️ Commit 4ddecea ("Actualización de credencial de Gmail"): verificar que no haya secretos hardcodeados en el historial; rotar la credencial si llegó a exponerse.
- ⚠️ PR #23 con base en `proyecto-angular-java`: NO fusionar sin decisión explícita.
- ⚠️ La app móvil sigue sin suite de tests propia; agregar smoke mínimo antes de sustentarla.
- ⚠️ `AGENTS.md` sigue fuera del repositorio (ignorado vía .gitignore).
- ℹ️ Verificar corrida de CI (GitHub Actions) sobre el HEAD actual (75cf32c) para confirmar los ~263 tests en verde.

### 📅 2026-10-26 — Revisión diaria de Vibe: app móvil y auth post-plan, email corporativo; PR draft #23 abierto

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, 25 oct, todos de S4mu0/agentes):**
- [chore: actualiza auditoria final y deja el proyecto listo para nuevas fases](https://github.com/angel123297/palacio-del-mar/commit/0307db42676ea7b7e04f79a3f370bca0b21e3290) (00:47 UTC).
- [feat: implementa recuperacion de contraseña y persistencia con refresh tokens (Fase 1)](https://github.com/angel123297/palacio-del-mar/commit/0d96c2f6600db92feb41ed4d3985acc6771f7376) (01:04 UTC) — recuperación de contraseña + refresh tokens.
- [feat: Fase 2 completada - historial de reservas, endpoints de cancelación y filtros avanzados en interfaz](https://github.com/angel123297/palacio-del-mar/commit/216dbb1f5fb04b3a99c2a625fc63d9e5d5589e4c) (02:20 UTC).
- [fix(backend): restaurar BookingService completo (216dbb1 dejó createBooking como stub)](https://github.com/angel123297/palacio-del-mar/commit/93e704f9192a03db4bec82b242b26a8513ce55e4) (03:25 UTC) — ⚠️ revert de un stub introducido horas antes.
- [fix(mobile): restaurar ApiService y corregir imports y cancelación de reservas](https://github.com/angel123297/palacio-del-mar/commit/9b97f8fba6c75469deb20b5b178fd0a9a0897d92) (03:47 UTC).
- [fix(backend): permitir arranque de PaymentWebhookService con @Autowired](https://github.com/angel123297/palacio-del-mar/commit/0ae25bba6247aeb55138586614a114e18c44f8b0) (04:36 UTC).
- [chore(docker): leer secretos de correo y JWT desde .env](https://github.com/angel123297/palacio-del-mar/commit/4c43bd0ca6d81b9150e258ce58db25334dc921eb) (04:36 UTC).
- [feat(web): mapa de sucursales con Leaflet en la home](https://github.com/angel123297/palacio-del-mar/commit/ec728c0dd80bf7eb0ec28f018de30813de0fdd33) (04:36 UTC).
- [docs(qa): reporte diario 2026-10-25](https://github.com/angel123297/palacio-del-mar/commit/cec8353782d01c899adefa69d694d7e16955a21d) y [docs(qa): restaurar QA_REPORTE.md completo](https://github.com/angel123297/palacio-del-mar/commit/5257ce47e04fb33b08d281aa2420d0d711c185bf) (14:47/14:48 UTC) — reportes de QA de Vibe.
- [fix(backend): Actualización de credencial de Gmail y correcciones de compilación](https://github.com/angel123297/palacio-del-mar/commit/4ddecea2898c99d5bf84413f94330b0c5162d96b) (17:23 UTC).
- [Merge branch 'feature/app-movil-fase4-completa' into plan/marketplace-webhooks](https://github.com/angel123297/palacio-del-mar/commit/1f27fc1615936e77da9777fe21c0a5c450dbf24c) (17:35 UTC) — integración de la app móvil (fase 4).
- [Fix: Estilo corporativo HTML en correos automáticos](https://github.com/angel123297/palacio-del-mar/commit/c2dffc20c49d80403a27d461f2a68bccad9a1e48) (17:42 UTC).
- [Fix: Enlace dinámico y funcional en el botón del correo de reserva](https://github.com/angel123297/palacio-del-mar/commit/75cf32c32155511b57f9a2eb85e99cc3d32c6527) (17:44 UTC) — HEAD actual de la rama.

**Pull requests:** **1 PR abierto:** [#23 — Cleanup unused frontend code and dependencies](https://github.com/angel123297/palacio-del-mar/pull/23) (draft, actualizado 26 oct 04:11 UTC, head `cleanup-unused-code-4999260079386501287`). ⚠️ Su base es `proyecto-angular-java` (rama original protegida): revisar y decidir cerrar o cambiar la base antes de cualquier fusión.

**Fase en curso:** plan de 5 fases **entregado** (fusionado el 21 oct). El trabajo nuevo es post-plan: **app móvil** (fases 1–4 móviles con merge `feature/app-movil-fase4-completa`), auth (recuperación de contraseña, refresh tokens), historial/cancelación de reservas, mapa Leaflet y correos HTML corporativos.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** ~263+ de backend y ~14+ de frontend (línea base del 22 oct; los commits del día tocan BookingService, PaymentWebhookService y auth, cubiertos por suites existentes, pero **ningún commit reporta corrida de tests**). Pendiente verificación local/CI.

**Riesgos / problemas detectados:**
- ⚠️ **Commit 4ddecea "Actualización de credencial de Gmail"**: posible credencial/secreto manejado en el repo. Verificar que no haya secretos hardcodeados y que estén en `.env` (no versionado); rotar la credencial si llegó a exponerse.
- ⚠️ 216dbb1 dejó `createBooking` como stub y fue restaurado en 93e704f 1 hora después: riesgo de regresión en reservas; verificar que `BookingServiceTest` y anti-overbooking (`SuiteNight`) estén en verde.
- ⚠️ PR #23 con base en `proyecto-angular-java` (rama original): NO fusionar sin decisión explícita del usuario.
- ⚠️ La app móvil nueva no aparece con tests propios; si se sustenta, agregar suite mínima de smoke.
- ⚠️ `AGENTS.md` sigue fuera del repositorio (df2c2e0, ignorado vía .gitignore).
- ℹ️ Persiste el riesgo del conteo de tests (~263) reportado por agentes sin verificación en CI por Vibe.

### 📅 2026-10-25 — Revisión diaria de Vibe: sin cambios en el día

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`):** solo 1 commit, y es el propio reporte diario de QA de ayer — [docs(qa): reporte diario 2026-10-24](https://github.com/angel123297/palacio-del-mar/commit/5ea0c08c2ac6ece9e1e62318eceda4f69139c51c) (24 oct, 14:40 UTC). **Sin cambios de código en el día**: ningún commit de implementación nuevo.

**Pull requests:** **0 PRs abiertos** (verificado vía GitHub API). Backlog de PRs sigue limpio desde el 21 oct (#20 fue el último, ya fusionado).

**Fase en curso:** plan de 5 fases **entregado** (fusionado a `proyecto-angular-java` el 21 oct). Sin avance post-plan en el día; la última actividad funcional sigue siendo la del 22 oct (auth/UX, calendario con turnover checkout, email real y builds Docker).

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** ~263+ de backend y ~14+ de frontend (cifras heredadas del 22 oct; sin commits de código, la línea base del 22 oct se mantiene). No se detectan fallos; riesgo menor: al no haber commits no hay evidencia fresca de CI del día — verificar Actions en el próximo commit.

### 📅 2026-10-24 — Revisión diaria de Vibe: sin cambios en el día

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`):** solo 1 commit, y es el propio reporte diario de QA de ayer — [docs(qa): reporte diario 2026-10-23](https://github.com/angel123297/palacio-del-mar/commit/fa1dd3383d6ad00caef665cf21ea1c434b9a9be1) (23 oct, 14:20 UTC). **Sin cambios de código en el día**: ningún commit de implementación nuevo.

**Pull requests:** **0 PRs abiertos** (verificado vía GitHub API). Backlog de PRs sigue limpio desde el 21 oct (#20 fue el último, ya fusionado).

**Fase en curso:** plan de 5 fases **entregado** (fusionado a `proyecto-angular-java` el 21 oct). Sin avance post-plan en el día (la última actividad funcional fue el 22 oct: auth/UX, calendario con turnover checkout, email real y builds Docker).

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — siguen confirmados corregidos y activados desde el 16 oct (`PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** ~263+ de backend y ~14+ de frontend (cifras heredadas del 22–23 oct; al no haber commits de código, la línea base del 22 oct se mantiene). No se detectan fallos; riesgo menor: sin commits no hay evidencia fresca de CI del día, se recomienda verificar Actions en el próximo commit.


### 📅 2026-10-23 — Revisión diaria de Vibe: fixes de auth/UX, calendario con días de rotación y builds Docker; sin PRs abiertos

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, todos del usuario/agentes, 22 oct 20:01–22:43 UTC):**
- [fix(docker): agregar reintentos de red y timeouts extendidos para npm y maven en builds](https://github.com/angel123297/palacio-del-mar/commit/d5e6590bab238de844fe775d711b8fe80005ddeb) (20:01 UTC).
- [feat(calendar): soportar dias de rotacion hotelera (turnover checkout) y filtrar reservas canceladas en motor de inventario](https://github.com/angel123297/palacio-del-mar/commit/02dacc9788285c4fa9de351a56843e913f0d83c2) (20:46 UTC) — turnover checkout en calendario; reservas canceladas ya no consumen inventario.
- [feat(auth,email,roles): separar portales corporativos, notificaciones por email real y restricciones de reservas para anfitrion/admin](https://github.com/angel123297/palacio-del-mar/commit/bb98b176684e362597536a15fabf8eadb2535fc3) (22:02 UTC) — email real (deja Mailpit como entorno de prueba), restricciones de rol para reservas.
- [fix(navbar): unificar acceso en botón iniciar sesión y remover portal corporativo redundante](https://github.com/angel123297/palacio-del-mar/commit/a93940f8ab1407dbfa1b1f9bdb458eb55e26e851) (22:15 UTC).
- [fix(auth): corregir mapeo de error de teléfono duplicado que mostraba erróneamente correo ya registrado](https://github.com/angel123297/palacio-del-mar/commit/feff000b77d91c12c6dd348c0c7a6cb45e4dcc4c) (22:27 UTC).
- [fix(docker): agregar valor por defecto de desarrollo para JWT_SECRET cuando falta archivo .env](https://github.com/angel123297/palacio-del-mar/commit/bd6587004506b41e16f5cfe7a373dae6c2e1ddbc) (22:27 UTC).
- [feat(auth): habilitar boton verificar ahora directamente en modal de inicio de sesion](https://github.com/angel123297/palacio-del-mar/commit/7ab6c75b4a860178c3b4fcd57089026d79375daf) (22:43 UTC) — HEAD actual de la rama.

**Pull requests:** **0 PRs abiertos.** Sin cambios de estado: todos los PRs previos siguen cerrados (backlog limpio desde el 21 oct).

**Fase en curso:** plan de 5 fases **entregado** (fusionado a `proyecto-angular-java` vía #20 y merge f5302db del 21 oct). Actividad post-plan: UX de autenticación, portales por rol, email transaccional real y robustez de builds Docker.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** ~263+ de backend y ~14+ de frontend (cifras heredadas del 21–22 oct; los commits de hoy tocan auth, calendario e email, cubiertos por suites existentes, pero **ningún commit reporta corrida de tests**). **Pendiente verificación local/CI** (el conector no expone corridas de Actions).

**Riesgos / problemas detectados:**
- ⚠️ El fix de JWT_SECRET por defecto para desarrollo (bd65870) debe quedar restringido a perfil dev: un valor por defecto en producción invalidaría el hallazgo PDM relacionado con el secret JWT. Verificar `application.yml`/perfiles.
- ⚠️ La validación de error de teléfono duplicado (feff000) sugiere que la BD ya no usa constraint única de teléfono o que el manejo era incompleto: revisar que exista validación a nivel de servicio y no solo mapeo del error.
- ⚠️ Email "real" (bb98b17): confirmar que las credenciales SMTP no estén hardcodeadas y que Mailpit siga disponible para entornos de prueba/sustentación.
- ⚠️ Cambios de calendario (02dacc9) tocan el motor de inventario: verificar que los tests de disponibilidad/anti-overbooking (`SuiteNight`) sigan en verde.
- ⚠️ Persiste la 2ª fusión sobre `proyecto-angular-java` (f5302db, 21 oct): si la regla es no modificar la rama original, confirmar si fue intencional.
- ⚠️ `AGENTS.md` sigue fuera del repositorio (df2c2e0, ignorado vía .gitignore).

### 📅 2026-10-22 — Revisión diaria de Vibe: migración TS/SCSS, plan de carga JMeter y fixes de calendario; sin PRs abiertos

**Commits de las últimas 24h (rama `plan/marketplace-webhooks`, todos del usuario/agentes):**
- [docs(qa): reporte diario 2026-10-21](https://github.com/angel123297/palacio-del-mar/commit/ed5a1835c6809f58f2e952ca0e7117d2e8ca2935) (21 oct, 14:57 UTC) — reporte diario previo de Vibe.
- [feat(perf): agregar plan de pruebas de carga jmeter y benchmark de concurrencia](https://github.com/angel123297/palacio-del-mar/commit/b65df4989dd57ea113694806fa2f9554c956e462) (21 oct, 16:55 UTC) — plan de pruebas de carga JMeter y benchmark de concurrencia.
- [Merge branch 'plan/marketplace-webhooks' into proyecto-angular-java](https://github.com/angel123297/palacio-del-mar/commit/f5302db901062934f552efb5b67f8c95e4a22616) (21 oct, 16:56 UTC) — ⚠️ **segunda fusión de la rama de trabajo sobre la rama original `proyecto-angular-java`** (ya se había fusionado #20 el 21 oct). Ver riesgos.
- [refactor(styles): migrar todos los estilos de css a scss y eliminar archivos huerfanos](https://github.com/angel123297/palacio-del-mar/commit/5183c621f4747fc44ed84bde97136f4f5d8d249c) (21 oct, 17:28 UTC).
- [refactor(lang): migrar utilidades y configuraciones de javascript a typescript puro](https://github.com/angel123297/palacio-del-mar/commit/dc1292b5381b009ac4c41ccb564df110c134d59e) (21 oct, 17:40 UTC).
- [feat(ux): polish guest, host and admin experiences for 10/10 bot rating](https://github.com/angel123297/palacio-del-mar/commit/b34ab94ab8ad4a7dd62c14a3758108ed4a4929fa) (21 oct, 19:19 UTC) — fallback de suites/sedes, barra de reserva, tarjetas de recomendación, creación unificada propiedad/suite, ACH limpio, calculadora 92%, moderación admin con motivos, modal de reembolsos.
- [feat(host): sync initial suite in suites modal and support digital wallets in ACH setup](https://github.com/angel123297/palacio-del-mar/commit/badf886a9e3a6c492b89412ac18598e501dc5cda) (21 oct, 19:30 UTC).
- [fix(calendar): resolver disponibilidad falsa de días ocupados, alias de suites y contratos de api](https://github.com/angel123297/palacio-del-mar/commit/d9f7c527453ae2f37211ee83c09ba44f82ebd07c) (21 oct, 20:52 UTC).
- [Merge branch 'fix/calendar-availability-logic-audit' into plan/marketplace-webhooks](https://github.com/angel123297/palacio-del-mar/commit/2189a62b86492f2041cee0b9c794601f67c5b508) (21 oct, 20:52 UTC) — integración del fix de calendario (HEAD actual de la rama).

**Pull requests:** **0 PRs abiertos.** El backlog quedó limpio tras el cierre de drafts del 21 oct (#22, #19, #21, #18, #12, #4, #3) y la fusión de #20. Sin cambios de estado hoy.

**Fase en curso:** Fase 5 (CI/CD, Swagger, Actuator y automaciones) **completada**; el plan de 5 fases está entregado (fusionado a `proyecto-angular-java` vía #20 y merge f5302db). La actividad del día es post-plan: calidad/UX (polish 10/10), migración técnica CSS→SCSS y JS→TypeScript puro, plan de pruebas de carga JMeter y corrección de disponibilidad de calendario.

**Bugs PDM-01/02/03 y tests @Disabled:** sin cambios — confirmados corregidos y activados desde el 16 oct (commit 1dbd8ee, `PriceIntegritySecurityTest` sin `@Disabled`). Los 10 hallazgos de AUDITORIA_SEGURIDAD.md permanecen cerrados.

**Tests esperados en verde:** ~263+ de backend y ~14+ de frontend (cifras heredadas del 21 oct; hoy no se agregaron ni quitaron suites de tests según los mensajes de commit, pero la migración SCSS/TS y el fix de calendario tocan código frontend/backend cubierto por tests). **Pendiente verificación local/CI** (el conector no expone corridas de Actions).

**Riesgos / problemas detectados:**
- ⚠️ **Fusión f5302db sobre `proyecto-angular-java`:** es la segunda vez que la rama original recibe merges de la rama de trabajo (la primera fue #20). Si la regla del proyecto es no modificar la rama original, este commit la viola; confirmar si fue intencional.
- ⚠️ Refactors grandes (CSS→SCSS, JS→TS) sin reporte de corrida de tests en los mensajes de commit: verificar que `npm run build`, Vitest y `./mvnw test` estén verdes antes de la sustentación.
- ⚠️ Fix de calendario (d9f7c52) corrige "disponibilidad falsa de días ocupados" y "contratos de api": revisar que `AvailabilityController`/`/api/availability/monthly` mantenga los tests de disponibilidad en verde.
- ⚠️ PR #22 (endpoints admin de exportación/aprobación de reservas) sigue cerrado sin fusión: pendiente confirmar si ya existe en la rama (sin auditar).
- ⚠️ `AGENTS.md` sigue fuera del repositorio (df2c2e0, ignorado vía .gitignore).
- ℹ️ Riesgo de conteo de tests (~263) reportado por agentes sin verificación en CI por Vibe: sigue pendiente.

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
