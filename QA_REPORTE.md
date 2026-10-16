# 🧪 QA Report — Palacio del Mar

Reporte único de QA del proyecto. Se actualiza **cada día a las 9:00 a.m.** (Colombia) con la tarea programada de Vibe. Aquí se registran los resultados de Jules y de Vibe.

## 📊 Estado actual (línea base, 15 oct 2026)

- Backend: **93/93 tests** en verde (última corrida de Jules: 14 oct, BUILD SUCCESS)
- Frontend: **8/8 tests** (4 archivos)
- **+20 tests intensivos** agregados por la auditoría de Vibe (17 activos + 3 `@Disabled` que documentan bugs PDM-01/02/03) → esperados al correr: ~110
- Bugs abiertos conocidos: PDM-01 (precio de comida desde el cliente), PDM-02 (rama insegura en parseExperiences), PDM-03 (HOST sin sedes lee reservas ajenas) → detalle y fixes en `AUDITORIA_SEGURIDAD.md`

## 🗓️ Reporte diario (bitácora)

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
