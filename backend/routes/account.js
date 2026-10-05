import express from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { createRateLimiter } from '../middleware/rateLimit.js';
import {
  listFavorites, favoriteIds, addFavorite, removeFavorite, exportMyData, deleteMyAccount
} from '../controllers/accountController.js';

const router = express.Router();
router.use(authMiddleware);

const sensitive = createRateLimiter({ windowMs: 10 * 60 * 1000, max: 5, message: 'Demasiados intentos. Espera unos minutos.' });

router.get('/favorites', listFavorites);
router.get('/favorites/ids', favoriteIds);
router.put('/favorites/:suiteId', addFavorite);
router.delete('/favorites/:suiteId', removeFavorite);
router.get('/export', sensitive, exportMyData);
router.delete('/', sensitive, deleteMyAccount);

export default router;
