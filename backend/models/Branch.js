import mongoose from 'mongoose';

// Sucursal de Palacio del Mar. Toda la empresa es una sola entidad: las
// sucursales solo cambian de ubicación, entorno y habitaciones.

const POI_TYPES = ['historia', 'playa', 'gastronomia', 'vida-nocturna', 'cultura', 'compras', 'naturaleza'];

const PointOfInterestSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true },
    type: { type: String, enum: POI_TYPES, required: true },
    walkMinutes: { type: Number, min: 0, max: 120, required: true }, // caminando desde la sucursal
    location: {
      lat: { type: Number, min: -90, max: 90 },
      lng: { type: Number, min: -180, max: 180 }
    }
  },
  { _id: false }
);

const branchSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, unique: true },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    zone: { type: String, required: true, trim: true }, // barrio / zona turística
    tagline: { type: String, trim: true, maxlength: 140 },
    description: { type: String, required: true },
    address: { type: String, required: true, trim: true },
    location: {
      lat: { type: Number, required: true, min: -90, max: 90 },
      lng: { type: Number, required: true, min: -180, max: 180 }
    },
    mainImage: { type: String },
    vibe: [{ type: String, trim: true, lowercase: true }], // ej. "histórico", "playero"
    highlights: [PointOfInterestSchema],
    phone: { type: String, trim: true },
    checkInTime: { type: String, default: '15:00' },
    checkOutTime: { type: String, default: '12:00' },
    active: { type: Boolean, default: true, index: true },
    order: { type: Number, default: 0 }
  },
  { timestamps: true }
);

branchSchema.index({ active: 1, order: 1 });

const Branch = mongoose.models.Branch || mongoose.model('Branch', branchSchema);

export default Branch;
export { POI_TYPES };
