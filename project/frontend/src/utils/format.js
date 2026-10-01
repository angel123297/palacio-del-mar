export const formatCOP = (value) =>
  new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0
  }).format(value || 0);

export const formatDate = (value) =>
  new Date(value).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });

export const nightsBetween = (checkIn, checkOut) => {
  const diff = new Date(checkOut) - new Date(checkIn);
  return Math.max(0, Math.round(diff / (1000 * 60 * 60 * 24)));
};

// Las fechas de check-in/check-out se guardan a medianoche UTC. Con
// formatDate() en Colombia (UTC-5) se mostraban un día antes; esta versión
// las lee en UTC para mostrar el día correcto.
export const formatCalendarDate = (value) =>
  new Date(value).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' });
