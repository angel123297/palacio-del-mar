import mongoose from 'mongoose';
import Branch from '../models/Branch.js';

/**
 * Convierte el parámetro ?branch= (slug o _id) en el _id de la sucursal.
 * Devuelve:
 *   undefined → no se pidió filtro
 *   null      → se pidió una sucursal que no existe (el llamador devuelve 404/vacío)
 *   ObjectId  → la sucursal
 */
export const resolveBranchId = async (param) => {
  if (param === undefined || param === null || param === '') return undefined;
  const value = String(param).trim().toLowerCase().slice(0, 80);
  if (/^[a-f0-9]{24}$/.test(value)) {
    return new mongoose.Types.ObjectId(value);
  }
  if (!/^[a-z0-9-]+$/.test(value)) return null;
  const branch = await Branch.findOne({ slug: value }).select('_id').lean();
  return branch ? branch._id : null;
};
