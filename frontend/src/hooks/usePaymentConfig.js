import { useEffect, useState } from 'react';
import api from '../api/client';

// Configuración de pagos (métodos, modo simulado, política de cancelación).
// Se pide una sola vez y se comparte entre el modal de reserva y el pago.
let cached = null;
let inflight = null;

export default function usePaymentConfig() {
  const [config, setConfig] = useState(cached);

  useEffect(() => {
    if (cached) return undefined;
    let alive = true;
    inflight = inflight || api.get('/payments/config')
      .then((res) => { cached = res.data.data; return cached; })
      .catch(() => null)
      .finally(() => { inflight = null; });
    inflight.then((c) => { if (alive && c) setConfig(c); });
    return () => { alive = false; };
  }, []);

  return config;
}
