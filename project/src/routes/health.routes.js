import { Router } from 'express';

import { checkLiveness, checkDbHealth, checkReadiness } from '../controllers/health.controller.js';



const router = Router();



router.get('/liveness', checkLiveness);

router.get('/db', checkDbHealth);

router.get('/readiness', checkReadiness);



export default router;
