import { validationResult } from 'express-validator';

/**
 * Antes de este cambio, ningún archivo de rutas llamaba a
 * validationResult(req): las reglas de express-validator (body/query/param)
 * se declaraban pero nunca se comprobaban, así que fechas mal formadas,
 * precios negativos, IDs inválidos, etc. pasaban directo al controlador.
 * Este middleware cierra ese hueco: se coloca después de cada batería de
 * reglas y corta la petición con un 400 si alguna falló.
 */
export const validateRequest = (req, res, next) => {
  const errors = validationResult(req);

  if (!errors.isEmpty()) {
    const list = errors.array({ onlyFirstError: true });
    return res.status(400).json({
      success: false,
      message: list[0]?.msg || 'Error de validación',
      errors: list.map(e => e.msg)
    });
  }

  next();
};

export default validateRequest;
