import Branch from '../models/Branch.js';
import Suite from '../models/Suite.js';

// Datos públicos de una sucursal + "desde $X" y cuántos tipos de habitación tiene.
const withSummary = async (branches) => {
  const rows = await Suite.aggregate([
    { $match: { available: true, branch: { $in: branches.map((b) => b._id) } } },
    { $group: { _id: '$branch', fromPrice: { $min: '$basePrice' }, roomTypes: { $sum: 1 } } }
  ]);
  const byBranch = new Map(rows.map((r) => [String(r._id), r]));
  return branches.map((b) => ({
    ...b,
    fromPrice: byBranch.get(String(b._id))?.fromPrice ?? null,
    roomTypes: byBranch.get(String(b._id))?.roomTypes ?? 0
  }));
};

/** GET /api/branches — sucursales activas (para el mapa y el selector de zona) */
export const getBranches = async (req, res) => {
  try {
    const branches = await Branch.find({ active: true }).sort({ order: 1 }).lean();
    res.json({ success: true, data: await withSummary(branches) });
  } catch (error) {
    console.error('[GetBranches Error]:', error);
    res.status(500).json({ success: false, message: 'Error al obtener las sucursales' });
  }
};

/** GET /api/branches/:slug */
export const getBranchBySlug = async (req, res) => {
  try {
    const slug = String(req.params.slug || '').toLowerCase();
    const branch = /^[a-z0-9-]{1,60}$/.test(slug)
      ? await Branch.findOne({ slug, active: true }).lean()
      : null;
    if (!branch) {
      return res.status(404).json({ success: false, message: 'Sucursal no encontrada' });
    }
    const [data] = await withSummary([branch]);
    res.json({ success: true, data });
  } catch (error) {
    console.error('[GetBranch Error]:', error);
    res.status(500).json({ success: false, message: 'Error al obtener la sucursal' });
  }
};
