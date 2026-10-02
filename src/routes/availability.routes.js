import { Router } from 'express';

import { getCalendar, getSuiteAvailability, getExperienceAvailability } from '../controllers/availability.controller.js';

// Arriba (modifica la línea existente):

import { getCalendar, getSuiteAvailability, getExperienceAvailability, clearAvailabilityCache } from '../controllers/availability.controller.js';

// Abajo (añade esta línea antes del export default router):

router.delete('/cache', clearAvailabilityCache);

const router = Router();



router.get('/calendar', getCalendar);

router.get('/suite/:suiteId', getSuiteAvailability);



// Aunque la ruta empiece por experiences, pertenece a la lógica de disponibilidad

router.get('/experiences/:id/availability', getExperienceAvailability);



export default router;
