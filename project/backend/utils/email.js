import nodemailer from 'nodemailer';

// ============================================
// ENVÍO DE EMAILS (OPCIONAL)
// ============================================
// El proyecto no venía con ningún proveedor de correo: los emails de
// confirmación, verificación y recuperación de contraseña solo se
// simulaban con console.log (y en el caso del token de recuperación,
// además se registraba en los logs de producción, un riesgo de
// seguridad real).
//
// Este módulo envía correos de verdad SOLO si hay credenciales SMTP en
// las variables de entorno (SMTP_HOST, SMTP_USER, SMTP_PASS). Si no las
// hay, no falla: en desarrollo deja constancia en consola para poder
// probar el flujo completo, y en producción no imprime nada sensible.

let transporter = null;
let loggedMissingConfig = false;

const isConfigured = () =>
  !!(process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS);

/** ¿Hay SMTP configurado? (para arranque, health y flujos que lo requieren) */
export const isEmailConfigured = isConfigured;

/**
 * Escapa datos dinámicos antes de interpolarlos en HTML (SEC-009).
 */
export const escapeHtml = (value) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

/** Solo permite enlaces http(s); cualquier otra cosa se descarta. */
const safeUrl = (url) => {
  try {
    const u = new URL(url);
    return u.protocol === 'http:' || u.protocol === 'https:' ? u.toString() : '#';
  } catch {
    return '#';
  }
};

const getTransporter = () => {
  if (!isConfigured()) return null;
  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: Number(process.env.SMTP_PORT) || 587,
      secure: Number(process.env.SMTP_PORT) === 465,
      auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS
      }
    });
  }
  return transporter;
};

const FROM = process.env.SMTP_FROM || '"Palacio del Mar" <reservas@palaciomar.co>';
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:8080';

/**
 * Envía un correo. Si no hay SMTP configurado, no hace nada (salvo un
 * aviso discreto una sola vez en consola, solo en desarrollo).
 */
const sendMail = async ({ to, subject, html, text }) => {
  const t = getTransporter();

  if (!t) {
    if (process.env.NODE_ENV === 'development') {
      // Buzón de pruebas local (BUG-008): en desarrollo el correo se imprime
      // en la consola del servidor en vez de exponer tokens en respuestas HTTP.
      if (!loggedMissingConfig) {
        loggedMissingConfig = true;
        console.log('[Email] SMTP no configurado: en desarrollo los correos se imprimen aquí en consola.');
      }
      console.log(`[Email:dev] Para: ${to}\n  Asunto: ${subject}\n  ${text || ''}`);
    } else {
      // Fallo operativo visible también en producción (BUG-007). Sin datos sensibles.
      console.error(`[Email] SMTP no configurado: NO se envió el correo "${subject}".`);
    }
    return { sent: false, reason: 'SMTP no configurado' };
  }

  await t.sendMail({ from: FROM, to, subject, html, text });
  return { sent: true };
};

export const sendVerificationEmail = async (email, name, token) => {
  const link = safeUrl(`${FRONTEND_URL}/verificar-email/${encodeURIComponent(token)}`);
  return sendMail({
    to: email,
    subject: 'Verifica tu email · Palacio del Mar',
    text: `Verifica tu email: ${link}`,
    html: `<p>Hola ${escapeHtml(name)},</p>
      <p>Gracias por registrarte en Palacio del Mar. Confirma tu email haciendo clic en el siguiente enlace:</p>
      <p><a href="${escapeHtml(link)}">${escapeHtml(link)}</a></p>
      <p>Este enlace vence en 24 horas.</p>`
  });
};

export const sendPasswordResetEmail = async (email, name, token) => {
  const link = safeUrl(`${FRONTEND_URL}/restablecer-contrasena?token=${encodeURIComponent(token)}`);
  return sendMail({
    to: email,
    subject: 'Recupera tu contraseña · Palacio del Mar',
    text: `Restablece tu contraseña: ${link}`,
    html: `<p>Hola ${escapeHtml(name)},</p>
      <p>Recibimos una solicitud para restablecer tu contraseña. Si fuiste tú, haz clic aquí:</p>
      <p><a href="${escapeHtml(link)}">${escapeHtml(link)}</a></p>
      <p>Este enlace vence en 1 hora. Si no solicitaste esto, ignora este correo.</p>`
  });
};

export const sendBookingConfirmationEmail = async (booking, guestEmail, guestName) => {
  const nights = booking.nights || Math.ceil((new Date(booking.checkOut) - new Date(booking.checkIn)) / (1000 * 60 * 60 * 24));
  return sendMail({
    to: guestEmail,
    subject: `Reserva recibida #${String(booking._id).slice(-6).toUpperCase()} · Palacio del Mar`,
    html: `<p>Hola ${escapeHtml(guestName)},</p>
      <p>Hemos recibido tu solicitud de reserva en Palacio del Mar.</p>
      <ul>
        <li><strong>Suite:</strong> ${escapeHtml(booking.suite?.name)}</li>
        <li><strong>Check-in:</strong> ${new Date(booking.checkIn).toLocaleDateString('es-CO', { timeZone: 'UTC' })}</li>
        <li><strong>Check-out:</strong> ${new Date(booking.checkOut).toLocaleDateString('es-CO', { timeZone: 'UTC' })}</li>
        <li><strong>Noches:</strong> ${nights}</li>
        <li><strong>Total:</strong> ${new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0 }).format(booking.totalPrice)}</li>
      </ul>
      <p>Tu reserva quedará <strong>pendiente de pago</strong> hasta que confirmemos el depósito. Te contactaremos por WhatsApp con las instrucciones.</p>`
  });
};

export default { sendVerificationEmail, sendPasswordResetEmail, sendBookingConfirmationEmail, isEmailConfigured, escapeHtml };
