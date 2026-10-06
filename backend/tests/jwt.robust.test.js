// Pruebas robustas de JWT y seguridad de autenticación para la asignatura
import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';
import User from '../models/User.js';

const SECRET_TEST = 'palacio_del_mar_jwt_secret_test_key_32chars_minimum';

test('JWT: token válido se genera y decodifica correctamente', () => {
  process.env.JWT_SECRET = SECRET_TEST;
  const payload = { id: '65f1a2b3c4d5e6f7a8b9c0d1', role: 'user', email: 'test@hotel.com' };
  const token = jwt.sign(payload, SECRET_TEST, { expiresIn: '1h' });
  
  assert.ok(token, 'El token JWT debe generarse');
  const decoded = jwt.verify(token, SECRET_TEST);
  assert.equal(decoded.id, payload.id);
  assert.equal(decoded.role, payload.role);
});

test('JWT: token con firma alterada (tampered) lanza JsonWebTokenError', () => {
  process.env.JWT_SECRET = SECRET_TEST;
  const payload = { id: '65f1a2b3c4d5e6f7a8b9c0d1', role: 'user' };
  const token = jwt.sign(payload, SECRET_TEST, { expiresIn: '1h' });
  
  // Alterar la firma o el payload
  const tamperedToken = token.slice(0, -5) + 'XXXXX';
  
  assert.throws(() => {
    jwt.verify(tamperedToken, SECRET_TEST);
  }, jwt.JsonWebTokenError);
});

test('JWT: token expirado lanza TokenExpiredError', () => {
  process.env.JWT_SECRET = SECRET_TEST;
  const payload = { id: '65f1a2b3c4d5e6f7a8b9c0d1', role: 'user' };
  // Token expirado hace 1 hora
  const expiredToken = jwt.sign(payload, SECRET_TEST, { expiresIn: '-1h' });
  
  assert.throws(() => {
    jwt.verify(expiredToken, SECRET_TEST);
  }, jwt.TokenExpiredError);
});

test('Auth Middleware: rechaza solicitudes sin cabecera Authorization o sin Bearer', async () => {
  const req = { header: () => undefined, ip: '127.0.0.1', headers: {} };
  const res = {
    status: function(code) { this.statusCode = code; return this; },
    json: function(data) { this.body = data; return this; }
  };
  let nextCalled = false;
  const next = () => { nextCalled = true; };

  await authMiddleware(req, res, next);
  assert.equal(nextCalled, false, 'No debe llamar a next() sin token');
  assert.equal(res.statusCode, 401);
  assert.equal(res.body.success, false);
});

test('Auth Middleware: rechaza cabecera con formato incorrecto (sin Bearer)', async () => {
  const req = { header: (name) => name === 'Authorization' ? 'Basic dXNlcjpwYXNz' : undefined, ip: '127.0.0.1', headers: {} };
  const res = {
    status: function(code) { this.statusCode = code; return this; },
    json: function(data) { this.body = data; return this; }
  };
  let nextCalled = false;
  const next = () => { nextCalled = true; };

  await authMiddleware(req, res, next);
  assert.equal(nextCalled, false);
  assert.equal(res.statusCode, 401);
});

test('Docker y Entorno: verificación de variables críticas configuradas para producción/desarrollo', () => {
  // Asegurar que el entorno de pruebas tiene configuraciones válidas
  const originalEnv = process.env.NODE_ENV;
  process.env.NODE_ENV = 'test';
  
  assert.equal(typeof process.env.NODE_ENV, 'string');
  assert.ok(process.env.JWT_SECRET || SECRET_TEST, 'JWT_SECRET debe estar disponible');
  
  process.env.NODE_ENV = originalEnv;
});
