# Host Registration & Dashboard Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable direct user registration as a Host (`role: host`), simplify the Host Dashboard into a human-friendly interface (separating technical Webhooks and PMS into a dedicated 1-Click "Integraciones & Demostración" tab), test with 3 specialized subagents (Admin, Host, Guest), and perform an automated security/code audit using GitHub Copilot CLI.

**Architecture:**
- Backend: Update `RegisterRequest` and `AuthService.register` to safely accept `role: "host"` (sanitizing and forbidding self-assignment of `"admin"`).
- Frontend: Enhance `auth-modal.component.ts` with a role selector (Huésped / Anfitrión) and add a "Pon tu alojamiento" CTA in `navbar.component.ts`.
- Host Dashboard: Refactor `host-dashboard.component.ts` tabs into 4 human-friendly primary tabs (Alojamientos, Reservas, Ganancias, Ofertas) and 1 consolidated "Integraciones & Demostración" tab with a 1-click reception simulator for academic presentations.
- Verification: 3 autonomous subagents (Admin, Host, Guest) and an automated GitHub Copilot CLI review.

**Tech Stack:** Spring Boot 3 (Java 21), Angular 18/19, Tailwind CSS, MongoDB, GitHub Copilot CLI.

**Spec:** User feedback on screenshots showing overly technical Host Dashboard tabs and requirement for self-service host registration + professor demo requirements.

## Global Constraints
- Never allow public registration with `role: "admin"` (must throw `IllegalArgumentException` or sanitize).
- Maintain all existing Webhook and API Key capabilities intact for academic demonstration.
- Preserve backward compatibility with existing tests (all 296 backend and 33 frontend tests must continue passing).

## Review Focus
- Security: Preventing privilege escalation to `admin` through registration payload.
- Usability: Host registration must immediately unlock host dashboard routes.
- Webhook Demo: The 1-click simulator must automatically register the internal receiver and display real-time events clearly.

---

### Task 1: Host Registration Support in Backend (TDD)
**Files:**
- Modify: `backend-java/src/main/java/co/palaciodelmar/backend/dto/RegisterRequest.java`
- Modify: `backend-java/src/main/java/co/palaciodelmar/backend/service/AuthService.java`
- Test: `backend-java/src/test/java/co/palaciodelmar/backend/service/AuthServiceTest.java`

**Steps:**
- [ ] Add failing test in `AuthServiceTest.java` verifying:
  - Registering with `role = "host"` creates user with `role = "host"`.
  - Registering with `role = "admin"` throws `IllegalArgumentException` or sanitizes to `"user"`.
  - Registering with null/blank `role` defaults to `"user"`.
- [ ] Add `private String role;` to `RegisterRequest.java`.
- [ ] Update `AuthService.java` to validate and set the sanitized role.
- [ ] Run `AuthServiceTest` -> verify green.

---

### Task 2: Host Registration UI in Frontend
**Files:**
- Modify: `frontend-angular/src/app/components/auth-modal/auth-modal.component.ts`
- Modify: `frontend-angular/src/app/components/navbar/navbar.component.ts`

**Steps:**
- [ ] In `auth-modal.component.ts`: Add role selection radio/pill buttons ("Quiero reservar (Huésped)" / "Quiero ser Anfitrión") in the register form.
- [ ] Send `role: 'host'` or `'user'` in the registration payload.
- [ ] If user registers as host, navigate to `/anfitrion` upon login.
- [ ] In `navbar.component.ts`: Add a prominent "Pon tu alojamiento" / "Publicar" CTA button that opens register modal in host mode or navigates to `/anfitrion`.

---

### Task 3: Host Dashboard Simplification & 1-Click Demo
**Files:**
- Modify: `frontend-angular/src/app/pages/host-dashboard/host-dashboard.component.ts`

**Steps:**
- [ ] Refactor navigation tabs into:
  1. `properties` -> "🏨 Mis Alojamientos"
  2. `bookings` -> "📅 Reservas & Calendario"
  3. `payouts` -> "💰 Mis Ganancias"
  4. `promotions` -> "🏷️ Ofertas y Descuentos"
  5. `integrations` -> "🔌 Integraciones & Modo Demostración"
- [ ] Consolidate Webhooks and API Keys under the `integrations` view.
- [ ] Add a prominent "⚡ Activar Simulador de Recepción en 1 Clic" button that automatically connects the demo receiver and starts live polling.
- [ ] Add an accordion/toggle "🔍 Ver datos técnicos (JSON / HMAC)" so technical details can be expanded for the professor on demand without cluttering the main UI.

---

### Task 4: Role-Based Autonomous Subagent Testing
**Subagents:**
- [ ] Subagent 1: **Admin Agent** - tests property review and moderation via admin endpoints.
- [ ] Subagent 2: **Host Agent** - tests registering as host, publishing property/suite, configuring bank account, and activating 1-click webhook simulation.
- [ ] Subagent 3: **Guest Agent** - tests guest registration, searching suites, booking, and confirming email dispatch.

---

### Task 5: GitHub Copilot CLI Security & Quality Review
**Steps:**
- [ ] Run `/home/angel19/.local/bin/copilot -p "Review git diff for security vulnerabilities, IDOR, privilege escalation, or code defects" --silent`.
- [ ] If Copilot flags any issues, address them immediately and re-run test suites.
- [ ] Run full backend and frontend regression suites to confirm 0 failures.
