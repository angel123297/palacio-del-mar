/**
 * Validación de email segura.
 *
 * La expresión anterior (/^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/) tenía
 * repeticiones anidadas: con un dominio largo que no encajaba (por ejemplo
 * "a@" + 40 letras + ".info") el motor de expresiones regulares probaba un
 * número exponencial de combinaciones y congelaba todo el servidor (ReDoS).
 * Además rechazaba dominios válidos de más de 3 letras (.info, .store...).
 *
 * Esta versión no tiene repeticiones ambiguas: las etiquetas del dominio no
 * pueden contener puntos, así que cada carácter se asigna de una única forma.
 * Aun así se limita la longitud ANTES de evaluar la expresión.
 */
export const EMAIL_MAX_LENGTH = 254;

const EMAIL_REGEX = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)*\.[^\s@.]{2,}$/;

export const isValidEmail = (value) =>
  typeof value === 'string' && value.length <= EMAIL_MAX_LENGTH && EMAIL_REGEX.test(value);
