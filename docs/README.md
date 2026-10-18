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

### 📋 4. Planes, Calidad y Seguridad
- **[PLAN_MARKETPLACE.md](PLAN_MARKETPLACE.md):** Especificación funcional del roadmap de 5 fases de transformación multi-anfitrión, modelo de datos y contratos de webhooks.
- **[QA_REPORTE.md](QA_REPORTE.md):** Bitácora oficial de aseguramiento de calidad (QA), suite de pruebas automatizadas (256 pruebas backend + 13 frontend) y verificación de no-regresión.
- **[AUDITORIA_SEGURIDAD.md](AUDITORIA_SEGURIDAD.md):** Matriz técnica de mitigaciones de seguridad: Anti-IDOR en endpoints de anfitrión, Anti-SSRF en webhooks, revocación duradera JWT y aleatoriedad criptográfica.

---

## 📂 Archivos Raíz del Repositorio

Para mantener el estándar y máxima limpieza en la raíz del repositorio, únicamente residen los archivos fundamentales:

| Archivo | Ubicación | Propósito |
| :--- | :--- | :--- |
| **README Principal** | `../README.md` | Guía de inicio rápido, arquitectura, despliegue Docker y credenciales. |
| **Reglas de Agentes IA** | `../AGENTS.md` | Directivas operativas y protocolo `non-breaking-delivery` para IA. |
| **Docker Compose** | `../docker-compose.yml` | Orquestación completa de servicios (MongoDB, Redis, Mailpit, Apps). |
| **Variables de Entorno** | `../.env.example` | Plantilla de configuración segura sin secretos expuestos. |

