import mongoose from 'mongoose';



// 1. Liveness (¿La API está encendida?) -> /health/liveness

export const checkLiveness = (req, res) => {

    res.status(200).json({ status: 'UP', message: 'Servidor Express corriendo' });

};



// 2. Database Health (¿Mongo está conectado?) -> /health/db

export const checkDbHealth = (req, res) => {

    const state = mongoose.connection.readyState;

    // 0: disconnected, 1: connected, 2: connecting, 3: disconnecting

    const status = state === 1 ? 'UP' : 'DOWN';

    

    res.status(state === 1 ? 200 : 503).json({ 

        database: 'MongoDB',

        status: status, 

        readyState: state 

    });

};



// 3. Readiness (¿Listo para recibir tráfico pesado?) -> /health/readiness

export const checkReadiness = (req, res) => {

    // Aquí podrías comprobar otros servicios si los tienes (ej. Redis)

    const isDbReady = mongoose.connection.readyState === 1;

    if (isDbReady) {

        res.status(200).json({ status: 'READY', timestamp: new Date() });

    } else {

        res.status(503).json({ status: 'NOT_READY' });

    }

};
