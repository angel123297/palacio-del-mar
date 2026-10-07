import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, symlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { isMainModule } from '../utils/isMain.js';

const helperUrl = pathToFileURL(path.resolve(import.meta.dirname, '../utils/isMain.js')).href;

const run = (file) => execFileSync(process.execPath, [file], { encoding: 'utf8' }).trim();

const setup = () => {
  const root = mkdtempSync(path.join(tmpdir(), 'is-main-'));
  const dir = path.join(root, 'carpeta con espacios y ñ'); // la URL codifica estos caracteres
  mkdirSync(dir);
  const script = path.join(dir, 'script.mjs');
  writeFileSync(script, `
    import { isMainModule } from '${helperUrl}';
    // guard ANTERIOR, para demostrar por qué fallaba
    const old = import.meta.url === \`file://\${process.argv[1]}\`;
    console.log(JSON.stringify({ main: isMainModule(import.meta.url), old }));
  `);
  const importer = path.join(dir, 'importer.mjs');
  writeFileSync(importer, `import './script.mjs';`);
  return { root, dir, script, importer };
};

test('detecta la ejecución directa aunque la ruta tenga espacios y caracteres especiales', () => {
  const { root, script } = setup();
  try {
    const out = JSON.parse(run(script));
    assert.equal(out.main, true);
    assert.equal(out.old, false, 'el guard anterior NO lo detectaba: el script no hacía nada');
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('detecta la ejecución directa a través de un enlace simbólico', () => {
  const { root, dir, script } = setup();
  try {
    const link = path.join(dir, 'enlace.mjs');
    symlinkSync(script, link);
    assert.equal(JSON.parse(run(link)).main, true);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('al importarlo desde otro módulo NO se considera ejecución directa', () => {
  const { root, importer } = setup();
  try {
    assert.equal(JSON.parse(run(importer)).main, false);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('sin argv[1] o con una ruta inexistente devuelve false sin lanzar error', () => {
  assert.equal(isMainModule('file:///x.js', undefined), false);
  assert.equal(isMainModule('file:///x.js', '/no/existe/nunca.js'), false);
});
