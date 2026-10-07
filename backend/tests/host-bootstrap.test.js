import test from 'node:test';
import assert from 'node:assert/strict';
import { autoCreateHostIfMissing, DEV_HOST_DEFAULTS } from '../bootstrap.js';

// Modelos simulados en memoria: sin base de datos.
const makeModels = ({ users = [], branches = [{ _id: 'b1' }, { _id: 'b2' }] } = {}) => {
  const db = { users: [...users], updates: [] };
  class User {
    constructor(data) { Object.assign(this, data); }
    async save() {
      if (db.users.some((u) => u.email === this.email)) { const e = new Error('dup'); e.code = 11000; throw e; }
      db.users.push(this);
    }
    static async findOne({ email }) { return db.users.find((u) => u.email === email) || null; }
    static async updateOne(filter, update) { db.updates.push({ filter, update }); }
  }
  const Branch = { find: () => ({ select: async () => branches }) };
  return { db, User, Branch };
};

const silence = (fn) => async (...a) => {
  const w = console.warn, l = console.log;
  console.warn = () => {}; console.log = () => {};
  try { return await fn(...a); } finally { console.warn = w; console.log = l; }
};

test('desarrollo: sin variables crea el anfitrión de ejemplo con todas las sucursales', silence(async () => {
  const { db, User, Branch } = makeModels();
  const r = await autoCreateHostIfMissing({ User, Branch, env: { NODE_ENV: 'development' } });
  assert.equal(r.created, true);
  assert.equal(db.users[0].email, DEV_HOST_DEFAULTS.email);
  assert.equal(db.users[0].role, 'host');
  assert.deepEqual(db.users[0].branches, ['b1', 'b2']);
}));

test('producción sin HOST_EMAIL/HOST_PASSWORD: no crea nada', silence(async () => {
  const { db, User, Branch } = makeModels();
  const r = await autoCreateHostIfMissing({ User, Branch, env: { NODE_ENV: 'production' } });
  assert.deepEqual(r, { created: false, reason: 'missing_env' });
  assert.equal(db.users.length, 0);
}));

test('producción con la contraseña de ejemplo o una débil: no crea nada', silence(async () => {
  for (const password of [DEV_HOST_DEFAULTS.password, 'corta1', 'soloLetrasLargas']) {
    const { db, User, Branch } = makeModels();
    const r = await autoCreateHostIfMissing({
      User, Branch, env: { NODE_ENV: 'production', HOST_EMAIL: 'h@hotel.co', HOST_PASSWORD: password }
    });
    assert.equal(r.reason, 'weak_password', password);
    assert.equal(db.users.length, 0);
  }
}));

test('producción con credenciales propias y fuertes: crea el anfitrión', silence(async () => {
  const { db, User, Branch } = makeModels();
  const r = await autoCreateHostIfMissing({
    User, Branch, env: { NODE_ENV: 'production', HOST_EMAIL: 'H@Hotel.co', HOST_PASSWORD: 'ClaveMuyLarga2026x' }
  });
  assert.equal(r.created, true);
  assert.equal(db.users[0].email, 'h@hotel.co');
}));

test('un anfitrión existente NO recibe de nuevo la contraseña por defecto en cada arranque', silence(async () => {
  const existing = { _id: 'u1', email: DEV_HOST_DEFAULTS.email, role: 'host', password: 'HASH-CAMBIADO', branches: ['b1'] };
  const { db, User, Branch } = makeModels({ users: [existing] });
  const r = await autoCreateHostIfMissing({ User, Branch, env: { NODE_ENV: 'development' } });
  assert.deepEqual(r, { created: false, updated: false });
  assert.equal(existing.password, 'HASH-CAMBIADO');
  assert.equal(db.updates.length, 0);
}));

test('un anfitrión sin sucursales las recibe, sin tocar nada más', silence(async () => {
  const existing = { _id: 'u1', email: DEV_HOST_DEFAULTS.email, role: 'host', password: 'HASH', branches: [] };
  const { db, User, Branch } = makeModels({ users: [existing] });
  const r = await autoCreateHostIfMissing({ User, Branch, env: { NODE_ENV: 'development' } });
  assert.equal(r.updated, true);
  assert.deepEqual(db.updates[0].update, { $set: { branches: ['b1', 'b2'] } });
}));

test('si el correo es de un cliente/admin, no se convierte en anfitrión ni se modifica', silence(async () => {
  for (const role of ['user', 'admin']) {
    const existing = { _id: 'u1', email: DEV_HOST_DEFAULTS.email, role, password: 'HASH' };
    const { db, User, Branch } = makeModels({ users: [existing] });
    const r = await autoCreateHostIfMissing({ User, Branch, env: { NODE_ENV: 'development' } });
    assert.equal(r.reason, 'email_in_use');
    assert.equal(existing.role, role);
    assert.equal(existing.password, 'HASH');
    assert.equal(db.updates.length, 0);
  }
}));

test('carrera entre dos arranques (E11000): no lanza error', silence(async () => {
  const { User, Branch } = makeModels();
  User.findOne = async () => null; // ambos creen que no existe
  User.prototype.save = async () => { const e = new Error('dup'); e.code = 11000; throw e; };
  const r = await autoCreateHostIfMissing({ User, Branch, env: { NODE_ENV: 'development' } });
  assert.deepEqual(r, { created: false, reason: 'race' });
}));
