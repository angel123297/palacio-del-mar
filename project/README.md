Período: 30 de septiembre – 1 de octubre de 2026

Este documento detalla las implementaciones recientes en el proyecto, documentando los cambios en la arquitectura del backend, la lógica de negocio y la interfaz de usuario, acompañados de los fragmentos de código estructurales correspondientes.

🔌 Backend y API: Estructura de Endpoints
Se construyó y conectó la capa de controladores y rutas para gestionar el catálogo, la disponibilidad, la administración, el envío de correos y el monitoreo de los contenedores (Health Checks).

Implementación representativa (Health Checks y Catálogo):

JavaScript
// src/controllers/health.controller.js
export const checkDbHealth = (req, res) => {
    const state = mongoose.connection.readyState;
    // 1 indica conexión establecida con MongoDB
    res.status(state === 1 ? 200 : 503).json({ 
        database: 'MongoDB', 
        status: state === 1 ? 'UP' : 'DOWN' 
    });
};

// src/routes/catalog.routes.js
import { Router } from 'express';
import { getSuiteTypes, getFeaturedExperiences } from '../controllers/catalog.controller.js';

const router = Router();
router.get('/suites/types', getSuiteTypes);
router.get('/experiences/featured', getFeaturedExperiences);
🏨 Lógica de Reservas y Validaciones
Se ajustaron las reglas de negocio en el backend para hacer las cotizaciones más precisas, requiriendo información vital del usuario y limitando los parámetros de reserva para evitar saturación.

Lógica estructural de las restricciones aplicadas:

JavaScript
// Límite de 5 experiencias por reserva existente
if (booking.experiences.length >= 5) {
    return res.status(400).json({ 
        error: "Se ha alcanzado el límite máximo de 5 experiencias por reserva." 
    });
}

// Obligatoriedad del número de teléfono en el registro
if (!req.body.phone) {
    return res.status(400).json({ 
        error: "El número de teléfono es un campo obligatorio para el registro." 
    });
}

// Recálculo dinámico de precios (Concepto)
const recalculateTotal = (basePrice, experiences, discount) => {
    const experiencesTotal = experiences.reduce((acc, curr) => acc + curr.price, 0);
    return (basePrice + experiencesTotal) - discount;
};
🖥️ Frontend y Experiencia de Usuario (UI/UX)
Se integraron los filtros visuales del catálogo, un panel detallado de reservas y próximas llegadas en el dashboard, edición completa del perfil de usuario y mejoras de navegación mediante menús fijos y calendarios reactivos.

Implementación conceptual del menú estático y peticiones de disponibilidad:

CSS
/* Barra de reserva y menú de navegación fijos */
.navbar-sticky, .reservation-bar {
    position: sticky;
    top: 0;
    z-index: 1000;
    background-color: var(--background-light);
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
JavaScript
// Consumo del endpoint de calendario para bloquear días agotados
const fetchSuiteAvailability = async (suiteId) => {
    try {
        const response = await fetch(`/api/availability/suite/${suiteId}`);
        const activeBookings = await response.json();
        // Mapeo de checkIn y checkOut para bloquear el DatePicker
        const disabledDates = activeBookings.map(b => ({
            start: new Date(b.checkIn),
            end: new Date(b.checkOut)
        }));
        return disabledDates;
    } catch (error) {
        console.error("Error cargando el calendario", error);
    }
};
⚙️ Configuración y Entorno (.env)
Se resolvieron conflictos de integración relacionados con las variables de entorno, asegurando que las credenciales locales y de producción permanezcan fuera del control de versiones.

Actualización del archivo .gitignore:

Plaintext
# Archivos de entorno y credenciales
.env
.env.local
.env.development
.env.production

# Dependencias
node_modules/
