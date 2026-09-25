#!/usr/bin/env node
const { execSync, spawn } = require('child_process');
const path = require('path');

console.log('=== Palacio del Mar - Inicio ===');

// Iniciar backend
console.log('Iniciando backend...');
const backend = spawn('node', ['backend/server.js'], {
    stdio: 'inherit',
    cwd: __dirname
});

backend.on('error', (err) => {
    console.error('Error iniciando backend:', err.message);
});

process.on('SIGINT', () => {
    backend.kill();
    process.exit();
});
