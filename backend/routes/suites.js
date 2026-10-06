import express from 'express';
import {
  getSuites,
  getSuiteById,
  getSuiteStatsRoute,
  getSuiteTypes,
  calculatePrice,
  createSuite,
  updateSuite,
  deleteSuite
} from '../controllers/suiteController.js';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';

const router = express.Router();

// Rutas públicas
router.get('/', getSuites);
router.get('/stats', getSuiteStatsRoute);
router.get('/types', getSuiteTypes);
router.post('/calculate-price', calculatePrice);
router.get('/:id', getSuiteById);

// Rutas protegidas (solo admin)
// Antes solo pedían authMiddleware: cualquier usuario registrado (el
// registro es abierto) podía crear, editar o borrar cualquier suite del
// catálogo. Se agrega adminMiddleware para exigir rol de administrador.
router.post('/', authMiddleware, adminMiddleware, createSuite);
router.put('/:id', authMiddleware, adminMiddleware, updateSuite);
router.delete('/:id', authMiddleware, adminMiddleware, deleteSuite);

export default router;
