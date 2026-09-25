import express from 'express';
import { 
  chatWithAI, 
  getChatStatus, 
  clearRateLimitCache 
} from '../controllers/chatController.js';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';

const router = express.Router();

// Rutas públicas
router.post('/', chatWithAI);
router.get('/status', getChatStatus);

// Ruta de administrador
router.post('/clear-cache', authMiddleware, adminMiddleware, clearRateLimitCache);

export default router;