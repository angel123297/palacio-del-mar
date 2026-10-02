import { Router } from 'express';

import { resendVerification } from '../controllers/auth.controller.js';



const router = Router();



router.post('/resend-verification', resendVerification);



export default router;
