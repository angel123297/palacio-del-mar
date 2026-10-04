// Configuración de pagos. HOY es una SIMULACIÓN (PAYMENTS_MODE=simulated): siempre
// aprueba y nunca recibe datos de tarjeta. Para una pasarela real se agrega un
// proveedor con la misma forma ({ name, charge }) y se elige con PAYMENTS_MODE.
import { simulatedProvider } from './simulatedProvider.js';

export const PAYMENT_METHODS = [
  { id: 'card', label: 'Tarjeta de crédito o débito' },
  { id: 'pse', label: 'PSE (débito desde tu banco)' },
  { id: 'nequi', label: 'Nequi' }
];

export const CURRENCY = 'COP';

export const paymentsMode = () => String(process.env.PAYMENTS_MODE || 'simulated').trim().toLowerCase();

/** Falla al arrancar si la configuración de pagos es peligrosa o desconocida. */
export const assertPaymentsConfig = (env = process.env) => {
  const mode = String(env.PAYMENTS_MODE || 'simulated').trim().toLowerCase();
  if (mode === 'simulated') {
    if (env.NODE_ENV === 'production') {
      throw new Error('PAYMENTS_MODE=simulated no se permite con NODE_ENV=production: configura una pasarela real.');
    }
    return mode;
  }
  throw new Error(`PAYMENTS_MODE="${mode}" no tiene proveedor configurado (solo existe "simulated").`);
};

export const getProvider = () => {
  assertPaymentsConfig();
  return simulatedProvider;
};
