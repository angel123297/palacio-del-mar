import { realpathSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

/**
 * ¿Se está ejecutando este módulo directamente (`node archivo.js`) y no importándolo?
 * Uso:  if (isMainModule(import.meta.url)) run();
 *
 * Comparar `import.meta.url === \`file://${process.argv[1]}\`` falla en Windows
 * (`file://C:\...` no es una URL válida), con espacios o caracteres especiales en la
 * ruta (la URL los codifica) y con enlaces simbólicos. Aquí la ruta se convierte a
 * URL con la misma función que usa Node y se resuelven los enlaces.
 */
export const isMainModule = (metaUrl, argv1 = process.argv[1]) => {
  if (!argv1) return false;
  try {
    return metaUrl === pathToFileURL(realpathSync(argv1)).href;
  } catch {
    return false;
  }
};
