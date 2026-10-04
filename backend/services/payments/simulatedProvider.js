import crypto from 'node:crypto';

/**
 * Proveedor SIMULADO: SIEMPRE aprueba. Solo recibe importe, método y ids:
 * nunca números de tarjeta (el formulario del sitio ni siquiera los envía).
 * Una pasarela real implementará esta misma función `charge`.
 */
export const simulatedProvider = {
  name: 'simulated',
  async charge({ amount, method }) {
    return {
      status: 'approved',
      providerRef: `SIM-${crypto.randomBytes(5).toString('hex').toUpperCase()}`,
      message: `Pago aprobado (simulación · ${method} · ${amount})`
    };
  }
};
