import express from 'express';
import {
  register,
  login,
  getMe,
  refreshToken,
  logout,
  changePassword,
  forgotPassword,
  resetPassword,
  verifyEmail,
  resendVerificationEmail,
  updateProfile,
  getAllUsers,
  getUserById,
  updateUserStatus,
  deleteUser,
  getProfileStats
} from '../controllers/authController.js';
import {
  authMiddleware,
  loginRateLimiter,
  blacklistToken,
  adminMiddleware
} from '../middleware/auth.js';
import { body } from 'express-validator';
import { validateRequest } from '../middleware/validate.js';

const router = express.Router();

// ============================================
// NOTA SOBRE VALIDACIÓN
// ============================================
// Antes esta ruta declaraba su propia batería de reglas con
// express-validator, pero en ningún punto del proyecto se llamaba a
// validationResult(req), así que esas reglas nunca se aplicaban (código
// muerto) y además contradecían al esquema Joi del controlador (p. ej.
// Joi rechazaba "confirmPassword" como campo no permitido mientras la ruta
// decía validarlo). Para no tener dos fuentes de verdad que se
// contradicen, la validación de auth.js vive solo en el controlador
// (Joi), que ya da mensajes claros en español.

// ============================================
// RUTAS PÚBLICAS
// ============================================

/**
 * @route   POST /api/auth/register
 * @desc    Registrar un nuevo usuario
 * @access  Public
 */
router.post('/register', register);

/**
 * @route   POST /api/auth/login
 * @desc    Iniciar sesión
 * @access  Public
 */
router.post('/login', loginRateLimiter, login);

/**
 * @route   POST /api/auth/forgot-password
 * @desc    Solicitar recuperación de contraseña
 * @access  Public
 */
router.post('/forgot-password', forgotPassword);

/**
 * @route   POST /api/auth/reset-password
 * @desc    Restablecer contraseña con token
 * @access  Public
 */
router.post('/reset-password', resetPassword);

/**
 * @route   GET /api/auth/verify-email/:token
 * @desc    Verificar email del usuario
 * @access  Public
 */
router.get('/verify-email/:token', verifyEmail);

/**
 * @route   POST /api/auth/resend-verification
 * @desc    Reenviar email de verificación
 * @access  Public
 */
router.post('/resend-verification', resendVerificationEmail);

// ============================================
// RUTAS PROTEGIDAS (Requieren autenticación)
// ============================================

/**
 * @route   GET /api/auth/me
 * @desc    Obtener información del usuario autenticado
 * @access  Private
 */
router.get('/me', authMiddleware, getMe);

/**
 * @route   PUT /api/auth/profile
 * @desc    Actualizar perfil del usuario
 * @access  Private
 */
const updateProfileValidation = [
  body('name')
    .optional()
    .trim()
    .isLength({ min: 2, max: 100 }).withMessage('El nombre debe tener entre 2 y 100 caracteres'),
  body('lastName')
    .optional()
    .trim()
    .isLength({ max: 100 }).withMessage('El apellido no puede exceder 100 caracteres'),
  body('phone')
    .optional()
    .matches(/^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,9}$/)
    .withMessage('Teléfono inválido'),
  body('preferences.language')
    .optional()
    .isIn(['es', 'en']).withMessage('Idioma debe ser "es" o "en"'),
  body('preferences.newsletter')
    .optional()
    .isBoolean().withMessage('Newsletter debe ser true o false')
];
router.put('/profile', authMiddleware, updateProfileValidation, validateRequest, updateProfile);

/**
 * @route   POST /api/auth/change-password
 * @desc    Cambiar contraseña
 * @access  Private
 */
router.post('/change-password', authMiddleware, changePassword);

/**
 * @route   POST /api/auth/refresh
 * @desc    Refrescar token de autenticación
 * @access  Private
 */
router.post('/refresh', authMiddleware, refreshToken);

/**
 * @route   POST /api/auth/logout
 * @desc    Cerrar sesión (invalida token)
 * @access  Private
 */
router.post('/logout', authMiddleware, (req, res) => {
  const token = req.header('Authorization')?.replace('Bearer ', '');
  if (token) {
    blacklistToken(token);
  }

  if (req.userData) {
    req.userData.logActivity('logout', { success: true }, req.ip, req.headers['user-agent']);
  }

  res.json({
    success: true,
    message: 'Sesión cerrada exitosamente'
  });
});

/**
 * @route   GET /api/auth/profile/stats
 * @desc    Obtener estadísticas del perfil del usuario
 * @access  Private
 */
router.get('/profile/stats', authMiddleware, getProfileStats);

// ============================================
// RUTAS DE ADMINISTRADOR (Requieren rol admin)
// ============================================

router.get('/admin/users', authMiddleware, adminMiddleware, getAllUsers);
router.get('/admin/users/:id', authMiddleware, adminMiddleware, getUserById);
router.put(
  '/admin/users/:id/status',
  authMiddleware,
  adminMiddleware,
  body('status').isIn(['active', 'inactive', 'suspended']).withMessage('Estado inválido'),
  validateRequest,
  updateUserStatus
);
router.delete('/admin/users/:id', authMiddleware, adminMiddleware, deleteUser);

// ============================================
// RUTA DE PRUEBA
// ============================================

router.get('/health', (req, res) => {
  res.json({
    success: true,
    message: 'Auth service is running',
    timestamp: new Date().toISOString()
  });
});

export default router;
