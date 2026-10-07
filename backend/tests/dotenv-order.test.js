import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const backend = path.resolve(import.meta.dirname, '..');
const url = (rel) => pathToFileURL(path.join(backend, rel)).href;

// Puntos de entrada: deben cargar .env ANTES que cualquier otro módulo, porque varios
// (utils/dates.js, utils/email.js...) leen process.env en el momento de importarse.
const ENTRY_POINTS = ['server.js', 'seed/seedData.js', 'scripts/createAdmin.js', 'scripts/seedExperiences.js', 'scripts/backfillSuiteNights.js'];

test('los puntos de entrada importan dotenv/config como PRIMER import', () => {
  for (const file of ENTRY_POINTS) {
    const firstImport = readFileSync(path.join(backend, file), 'utf8')
      .split('\n').find((l) => /^import\s/.test(l));
    assert.equal(firstImport?.trim(), "import 'dotenv/config';", `${file}: el primer import es ${firstImport}`);
  }
});

test('con dotenv/config primero, un módulo que lee process.env al importarse ve el .env', () => {
  const cwd = mkdtempSync(path.join(tmpdir(), 'dotenv-order-'));
  try {
    writeFileSync(path.join(cwd, '.env'), 'HOTEL_TIMEZONE=Pacific/Kiritimati\n');
    const run = (body) => {
      const file = path.join(cwd, 'probe.mjs');
      writeFileSync(file, body);
      const env = { ...process.env }; delete env.HOTEL_TIMEZONE;
      return execFileSync(process.execPath, [file], { cwd, env, encoding: 'utf8' }).trim();
    };
    // Orden correcto (el de los puntos de entrada)
    assert.equal(run(`
      import '${url('node_modules/dotenv/config.js')}';
      import { HOTEL_TIMEZONE } from '${url('utils/dates.js')}';
      console.log(HOTEL_TIMEZONE);
    `), 'Pacific/Kiritimati');
    // Orden anterior (config() después de los imports): el módulo ya había leído el valor por defecto
    assert.equal(run(`
      import dotenv from '${url('node_modules/dotenv/lib/main.js')}';
      import { HOTEL_TIMEZONE } from '${url('utils/dates.js')}';
      dotenv.config();
      console.log(HOTEL_TIMEZONE);
    `), 'America/Bogota');
  } finally { rmSync(cwd, { recursive: true, force: true }); }
});
