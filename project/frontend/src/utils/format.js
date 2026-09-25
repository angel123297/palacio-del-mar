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
