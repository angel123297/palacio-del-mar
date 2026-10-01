import { Router } from 'express';

import { getSuiteTypes, getExperienceCategories, getFeaturedExperiences } from '../controllers/catalog.controller.js';



const router = Router();



router.get('/suites/types', getSuiteTypes);

router.get('/experiences/categories', getExperienceCategories);

router.get('/experiences/featured', getFeaturedExperiences);



export default router;
