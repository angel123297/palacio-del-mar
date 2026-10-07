import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveSuiteBranch } from './stayLocation.js';

const loc = { lat: 10.42, lng: -75.55 };
const branches = [
  { _id: 'b1', slug: 'centro', name: 'Centro', location: loc, address: 'Calle 1' },
  { _id: 'b2', slug: 'bocagrande', name: 'Bocagrande' } // sin coordenadas
];

test('usa la sucursal de la suite si ya trae coordenadas', () => {
  const suite = { branch: { _id: 'b9', name: 'X', location: loc } };
  assert.equal(resolveSuiteBranch(suite, branches), suite.branch);
});

test('completa la sucursal por _id cuando el listado no trae ubicación', () => {
  const suite = { branch: { _id: 'b1', name: 'Centro', slug: 'centro', zone: 'Z' } };
  assert.equal(resolveSuiteBranch(suite, branches).address, 'Calle 1');
});

test('completa por slug si falta el _id, y acepta un id suelto', () => {
  assert.equal(resolveSuiteBranch({ branch: { slug: 'centro' } }, branches)._id, 'b1');
  assert.equal(resolveSuiteBranch({ branch: 'b1' }, branches)._id, 'b1');
});

test('sin coordenadas, sin sucursal o sin lista: devuelve null (no se pinta el mapa)', () => {
  assert.equal(resolveSuiteBranch({ branch: 'b2' }, branches), null);
  assert.equal(resolveSuiteBranch({}, branches), null);
  assert.equal(resolveSuiteBranch(null, branches), null);
  assert.equal(resolveSuiteBranch({ branch: 'b1' }, []), null);
  assert.equal(resolveSuiteBranch({ branch: 'b1' }, undefined), null);
});
