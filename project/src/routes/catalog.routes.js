import { Router } from 'express';
import { getSuiteTypes, getExperienceCategories, getFeaturedExperiences, getSuiteById, getExperienceById } from '../controllers/catalog.controller.js';
import { getSuiteTypes, getExperienceCategories, getFeaturedExperiences } from '../controllers/catalog.controller.js';



const router = Router();



router.get('/suites/types', getSuiteTypes);

router.get('/experiences/categories', getExperienceCategories);

router.get('/experiences/featured', getFeaturedExperiences);

router.get('/suites/:id', getSuiteById);

router.get('/experiences/:id', getExperienceById);

export default router;
