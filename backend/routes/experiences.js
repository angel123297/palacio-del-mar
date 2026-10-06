import express from 'express';
import {
  getExperiences,
  getExperienceById,
  getExperienceCategories,
  getExperienceStatsRoute,
  getFeaturedExperiences,
  checkExperienceAvailability,
  createExperience,
  updateExperience,
  deleteExperience
} from '../controllers/experienceController.js';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';

const router = express.Router();

// Rutas públicas
router.get('/', getExperiences);
router.get('/stats', getExperienceStatsRoute);
router.get('/categories', getExperienceCategories);
router.get('/featured', getFeaturedExperiences);
router.get('/:id', getExperienceById);
router.get('/:id/availability', checkExperienceAvailability);

// Rutas protegidas (solo admin)
// Igual que en suites.js: antes cualquier usuario registrado podía crear,
// editar o borrar experiencias. Se exige rol de administrador.
router.post('/', authMiddleware, adminMiddleware, createExperience);
router.put('/:id', authMiddleware, adminMiddleware, updateExperience);
router.delete('/:id', authMiddleware, adminMiddleware, deleteExperience);

export default router;
