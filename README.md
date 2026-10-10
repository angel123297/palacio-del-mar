# Palacio del Mar - Enterprise Edition

Bienvenido a la versión refactorizada de **Palacio del Mar**, un sistema de reservas hoteleras de lujo migrado completamente a un stack tecnológico de grado empresarial.

Este repositorio contiene la entrega final del proyecto. La antigua arquitectura de Node/React ha sido reemplazada por una infraestructura mucho más robusta y escalable construida sobre **Java y Angular**.

## Tecnologías Utilizadas

- **Backend:** Java 21, Spring Boot 4.1, Spring Security, Spring Data MongoDB.
- **Frontend:** Angular 18 (Standalone Components, Signals), RxJS.
- **Base de Datos:** MongoDB 7.0.
- **Infraestructura:** Docker & Docker Compose.

## Características Principales

- **Gestión Concurrente de Reservas:** Optimistic Locking y control transaccional para evitar race-conditions (dobles reservas) en fechas agotadas.
- **Seguridad de Extremo a Extremo:** Autenticación por JWT, Guards de Angular para roles específicos (User, Admin, Host), validación de payloads y GlobalExceptionHandling para protección contra inyecciones.
- **Panel Multi-Rol:** Dashboards exclusivos y protegidos para el Usuario (historial y estado de reservas), el Anfitrión (gestión de ocupación por sucursal) y el Administrador (control total del sistema).
- **Tarifas Dinámicas y Fechas Pico:** Cálculos de precios según promociones y temporadas dinámicas integrados directamente en el DateRangePicker reactivo del frontend.

## Instalación y Ejecución Local

El proyecto está dockerizado para garantizar que se ejecute en cualquier entorno con un solo comando. No necesitas tener Java, Maven, Node o MongoDB instalados localmente, solo Docker.

1. Clona este repositorio y asegúrate de estar en la rama correcta.
2. Copia el archivo `.env.example` a `.env` (si es necesario) para configurar los secretos.
3. Levanta todos los servicios utilizando Docker Compose:

   ```bash
   docker compose up -d --build
   ```

Esto levantará los siguientes servicios:
- **Base de Datos MongoDB:** Puerto `27017`
- **Backend (Spring Boot API):** `http://localhost:5000`
- **Frontend (Angular):** `http://localhost:4200`
- **Servidor de Correos de Prueba (Mailpit):** `http://localhost:8025`

La aplicación completa estará disponible de inmediato en tu navegador accediendo a `http://localhost:4200`.

## Cuentas de Prueba

La base de datos se inicializa automáticamente (seed) con datos predeterminados, incluyendo sucursales, habitaciones y experiencias. Puedes usar las siguientes cuentas para probar los diferentes paneles:

- **Administrador:** 
  - Email: `admin@palaciodelmar.co`
  - Contraseña: `Admin123456!`
- **Anfitrión (Host):**
  - Email: `anfitrion@palaciodelmar.co`
  - Contraseña: `Host123456!`
- **Usuario Invitado:**
  - Puedes crear una cuenta directamente desde la aplicación.

## Estructura del Repositorio

- `/backend-java`: Código fuente de la API RESTFUL en Spring Boot. Arquitectura por capas (Controllers, Services, Repositories).
- `/frontend-angular`: Proyecto SPA en Angular.
- *Nota:* El código de la antigua versión Node/React se mantiene únicamente como referencia histórica bajo la rama `main` original o en la carpeta `referencia-node/`.

## Pruebas de Calidad (QA)

El backend cuenta con una suite de pruebas rigurosas utilizando JUnit y Mockito, garantizando la lógica transaccional de las reservas. Para ejecutarlas manualmente (requiere Java 21 y Maven instalados):
```bash
cd backend-java
./mvnw test
```
*(Tests aprobados: 72/72, 100% de cobertura en flujos de precios y cancelación).*
