# Validación de Negocio e Integración del Módulo de Promociones por Rango de Fechas — Palacio del Mar

Este documento valida formalmente la lógica de negocio propuesta para la creación de **promociones por rango de fechas con títulos descriptivos** por parte del Administrador o Anfitrión, y establece el plan técnico para implementarlo en Palacio del Mar.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Dictamen de Validación de Negocio: ¿Tiene lógica lo que planteas?**
> **SÍ, TIENE TOTAL Y ABSOLUTA LÓGICA DE NEGOCIO.**
> 
> En la industria hotelera y de hospitalidad de lujo (*Revenue Management*):
> 1. **Gestión Dinámica de Ocupación**: Los hoteles tienen valles de ocupación (ej. martes a jueves, semanas de temporada baja o cancelaciones de última hora). La capacidad de fijar una promoción temporal en un rango de fechas específico (ej. del 10 al 15 de Noviembre) con un descuento atractivo es la herramienta principal para evitar habitaciones vacías y maximizar el RevPAR (*Revenue Per Available Room*).
> 2. **Lógica de Roles (Admin vs. Anfitrión)**:
>    - El **Administrador** tiene visión global y puede crear promociones institucionales (ej. *"Aniversario Palacio del Mar - 20% OFF en todas las sedes"*).
>    - El **Anfitrión** vive la realidad operativa de su sede. Si la sede de Getsemaní tiene baja demanda la próxima semana, el anfitrión de Getsemaní debe poder lanzar una promoción localizada (ej. *"Fin de Semana Bohemio en Getsemaní - 15% OFF"*).
> 3. **Alineación con el Código de tu Repositorio**:
>    - En tu backend, la entidad `Promotion.java` ya fue diseñada exactamente para esto (`title`, `branch`, `discountPercent`, `startDate`, `endDate`, `active`).
>    - El buscador (`date-range-picker`) ya cuenta con la llamada `/api/availability/promotions`.
>    - **El problema era que faltaba la interfaz gráfica**: Ni el panel de Admin ni el de Anfitrión tenían el formulario para crear, ver y eliminar estas promociones. Incorporarlo cierra una brecha crítica y conecta una funcionalidad que ya tenías pensada.

---

## 1. Especificación del Módulo de Promociones (Admin & Host)

### 1.1 Modelo de Datos de la Promoción
```typescript
interface Promotion {
  id: string;
  title: string;          // Ej: "Escapada Romántica de Mitad de Semana"
  branchId?: string;      // Sede específica o 'all' (solo Admin puede 'all')
  discountPercent: number;// Entre 5% y 50%
  startDate: string;      // YYYY-MM-DD
  endDate: string;        // YYYY-MM-DD
  active: boolean;        // true/false
  createdByRole: 'admin' | 'host';
  createdByName: string;
}
```

### 1.2 Reglas de Negocio y Validación
1. **Rango de Fechas**:
   - `startDate >= hoy` (no se pueden crear promociones en el pasado).
   - `endDate >= startDate`.
2. **Tope de Descuento**:
   - Descuento entre **5% y 50%** (para evitar errores tipográficos como poner 90% o 100% que perjudiquen financieramente al hotel).
3. **Permisos por Rol**:
   - **Administrador**: Puede crear promociones para cualquier sede o globales.
   - **Anfitrión**: Solo puede crear promociones para las sedes que tiene asignadas (`user.branches`).
4. **Impacto en el Huésped**:
   - Si las fechas de la reserva del huésped caen dentro del rango de una promoción activa para esa sede, el sistema aplica automáticamente el descuento más favorable.
   - En el calendario `date-range-picker`, los días en promoción se destacan visualmente con un distintivo dorado y el porcentaje de descuento.

---

## 2. Mapa de Integración en Frontend y Backend

```
┌────────────────────────────────────────────────────────────────────────┐
│                     ADMIN & HOST DASHBOARDS                            │
│                                                                        │
│  [Pestaña "Promociones & Ofertas"]                                     │
│  ├── Formulario: Título, Sede, Rango de Fechas (Desde/Hasta), % OFF    │
│  ├── Listado de Promociones Activas con badge de estado y vigencia     │
│  └── Botón para pausar o eliminar promociones                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ POST /api/admin/promotions
                                    │ POST /api/host/promotions
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        BACKEND ENGINE                                  │
│                                                                        │
│  1. Validación de fechas (futuras, endDate >= startDate)               │
│  2. Validación de permisos (Host limitado a sus sucursales)            │
│  3. Persistencia en almacén de promociones                             │
│  4. Cálculo automático en /api/suites/calculate-price y /api/bookings  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         EXPERIENCIA DEL HUÉSPED                        │
│                                                                        │
│  - Calendario: Días con promoción destacados con icono de oferta       │
│  - Checkout: Desglose "Descuento Promocional: -$ XXX.XXX COP (XX%)"    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Matriz Consolidada de Lógica de Negocio (Actualizada)

### 👤 Rol Huésped / Usuario
- Enviar `balanceDue` en checkout (evitar error de sobrepago).
- Soporte para pagos parciales y cuotas.
- Unificar flujo de reserva para invitados y redirigir a checkout.
- Botón de cancelación en `my-bookings` (100% devolución >7d, 90% $\le$7d).
- Temporizador visual de 15 minutos en checkout.
- Filtro de sedes normalizado en el catálogo principal.
- Reflejo automático de las promociones creadas por Admin/Host.

### 👑 Rol Administrador
- **Gestor Visual de Promociones**: Formulario interactivo para crear, activar y eliminar promociones con rango de fechas y título atractivo.
- Conectar handler `(click)` en botón "Aprobar".
- Cálculo de ingresos netos reales (`paidAmount - refundedAmount`).
- Bloqueo de suites por mantenimiento/reparaciones.
- Exportación de reservas a archivo CSV.

### 🛎️ Rol Anfitrión
- **Gestor Local de Promociones**: Crear promociones con rango de fechas para su sede específica.
- Conectar enlace del botón "Gestionar" en la tabla de huéspedes.
- Registro operativo de Check-in y Check-out físico en recepción.
- Consulta y regeneración del PIN de cerradura digital (`smartLockPin`).
- Métrica de llegadas filtrada estrictamente para el día en curso.

---

## 4. Plan de Ejecución y Pruebas

1. **Implementación de Endpoints y Lógica de Promociones**:
   - Crear/ajustar endpoints: `GET /api/promotions`, `POST /api/promotions`, `DELETE /api/promotions/:id`.
   - Incorporar validaciones de fecha, descuento (5%-50%) y autorización de sede.
2. **Interfaz de Usuario en Admin y Host Dashboard**:
   - Añadir panel de control de promociones con formulario intuitivo y tabla de promociones activas.
3. **Conexión con el Checkout y Buscador**:
   - Asegurar que las reservas en rango promocional apliquen el descuento automáticamente.
4. **Verificación Automatizada**:
   - Pruebas con `curl` creando promociones válidas e inválidas (fechas pasadas, descuentos fuera de rango).
   - Verificación de compilación limpia con `compile_applet`.
