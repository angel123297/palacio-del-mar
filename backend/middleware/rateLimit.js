// Limitador de peticiones en memoria (un solo proceso; basta para este monolito).
// key: función que devuelve la clave (por defecto el usuario autenticado o la IP).
export const createRateLimiter = ({ windowMs, max, message, key } = {}) => {
  const hits = new Map(); // clave -> [timestamps]

  const sweep = setInterval(() => {
    const cutoff = Date.now() - windowMs;
    for (const [k, list] of hits) {
      const kept = list.filter((t) => t > cutoff);
      if (kept.length) hits.set(k, kept); else hits.delete(k);
    }
  }, Math.max(windowMs, 60000));
  sweep.unref();

  const middleware = (req, res, next) => {
    const id = (key ? key(req) : req.user?.id) || req.ip;
    const now = Date.now();
    const list = (hits.get(id) || []).filter((t) => t > now - windowMs);
    if (list.length >= max) {
      const retryAfter = Math.max(1, Math.ceil((list[0] + windowMs - now) / 1000));
      res.set('Retry-After', String(retryAfter));
      return res.status(429).json({
        success: false,
        message: message || 'Demasiados intentos. Espera un momento e inténtalo de nuevo.',
        retryAfterSeconds: retryAfter
      });
    }
    list.push(now);
    hits.set(id, list);
    next();
  };
  middleware.reset = () => hits.clear();
  return middleware;
};
