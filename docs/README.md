# 📚 Centro de Documentación — Palacio del Mar

Bienvenido al índice central de documentación y guías técnicas de **Palacio del Mar**. Este directorio organiza las especificaciones de arquitectura, guiones de sustentación, diagramas interactivos y reportes de auditoría.

---

## 🧭 Mapa de Documentación

### 🏛️ 1. Arquitectura y Secuencia Interactiva
- **[architecture.html](architecture.html):** Diagrama interactivo de arquitectura del marketplace (Spring Boot 3, MongoDB, Redis, Mailpit y Angular 18 con Signals). Permite inspeccionar contratos, componentes, modelos de datos y flujo perimetral.
- **[sequence.html](sequence.html):** Diagrama de secuencia lógica interactivo con el ciclo de vida completo de 13 pasos (reserva, bloqueo transaccional `SuiteNight`, pago asíncrono, verificación HMAC, despacho de webhooks y liquidación al anfitrión).

### 🎬 2. Guía de Sustentación & Demostración E2E
- **[DEMO_SCRIPT.md](DEMO_SCRIPT.md):** Playbook paso a paso (8 a 12 minutos) preparado para presentaciones y defensas ante jurado o evaluadores:
  1. Arquitectura y Modelo Marketplace (8% comisión / 92% anfitrión).
  2. Publicación de Alojamientos y Moderación Admin (`PENDING` ➔ `APPROVED`).
  3. Webhooks Salientes con firma HMAC-SHA256 y API Keys.
  4. Sincronización desde PMS Externo y prueba en vivo Anti-Overbooking.
  5. Checkout con tarjeta simulada, webhook asíncrono y notificaciones Mailpit.
  6. Comprobante imprimible con QR offline SVG y PIN de cerradura electrónica.
  7. Feeds iCal RFC 5545 para Airbnb / Google Calendar.
  8. Banco de preguntas frecuentes técnicas y respuestas ganadoras.

### 📝 3. Historial de Correcciones y Evolución Técnica
- **[CORRECCIONES.md](CORRECCIONES.md):** Bitácora cronológica exhaustiva de auditorías de código, transiciones de seguridad, corrección de endpoints de anfitrión y refactorizaciones anti-regresión.

---

## 📂 Enlaces a Reportes Raíz del Proyecto

Para mantener el estándar del repositorio, los reportes operativos principales se encuentran indexados desde aquí:

| Documento | Ubicación | Propósito |
| :--- | :--- | :--- |
| **README Principal** | `../README.md` | Guía de inicio rápido, despliegue Docker y credenciales. |
| **Plan del Marketplace** | `../PLAN_MARKETPLACE.md` | Roadmap de 5 fases de transformación multi-anfitrión y contratos. |
| **Reporte de QA y Tests** | `../QA_REPORTE.md` | Bitácora de calidad diaria y estado de suites (256 BE + 13 FE). |
| **Auditoría de Seguridad** | `../AUDITORIA_SEGURIDAD.md` | Matriz de vulnerabilidades mitigadas (Anti-IDOR, Anti-SSRF, JWT Blacklist). |
| **Reglas de Agentes IA** | `../AGENTS.md` | Directivas de desarrollo seguro (`non-breaking-delivery`) para IA. |
