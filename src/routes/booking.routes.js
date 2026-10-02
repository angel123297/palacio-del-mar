import { Router } from 'express';

import { confirmBooking } from '../controllers/booking.controller.js';



const router = Router();



router.post('/:id/confirm', confirmBooking);



export default router;
