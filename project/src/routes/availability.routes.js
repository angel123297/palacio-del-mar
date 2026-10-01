import { Router } from 'express';

import { getCalendar, getSuiteAvailability, getExperienceAvailability } from '../controllers/availability.controller.js';



const router = Router();



router.get('/calendar', getCalendar);

router.get('/suite/:suiteId', getSuiteAvailability);



// Aunque la ruta empiece por experiences, pertenece a la lógica de disponibilidad

router.get('/experiences/:id/availability', getExperienceAvailability);



export default router;
