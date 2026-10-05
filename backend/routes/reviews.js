import express from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { createRateLimiter } from '../middleware/rateLimit.js';
import { createReview, listSuiteReviews, getBookingReviewState } from '../controllers/reviewController.js';

const router = express.Router();
const limiter = createRateLimiter({ windowMs: 10 * 60 * 1000, max: 10, message: 'Demasiadas reseñas seguidas. Espera un momento.' });

router.get('/suite/:suiteId', listSuiteReviews);
router.get('/booking/:bookingId', authMiddleware, getBookingReviewState);
router.post('/', authMiddleware, limiter, createReview);

export default router;
