import express from 'express';
import { getBranches, getBranchBySlug } from '../controllers/branchController.js';

const router = express.Router();

// Públicas: el huésped explora las sucursales antes de registrarse
router.get('/', getBranches);
router.get('/:slug', getBranchBySlug);

export default router;
