import Booking from '../models/Booking.js'; // Ajusta si tu modelo se llama diferente (ej. Reserva)



// 1. Mapa general del calendario (Ideal para un dashboard de Admin)

export const getCalendar = async (req, res) => {

    try {

        // Busca reservas que no estén canceladas

        const bookings = await Booking.find({ status: { $ne: 'cancelled' } })

                                      .select('checkIn checkOut suite');

        res.status(200).json(bookings);

    } catch (error) {

        res.status(500).json({ message: 'Error al cargar el calendario', error: error.message });

    }

};



// 2. Disponibilidad de una Suite específica (Para bloquear fechas en el DatePicker)

export const getSuiteAvailability = async (req, res) => {

    try {

        const { suiteId } = req.params;

        const bookings = await Booking.find({ 

            suite: suiteId, 

            status: { $ne: 'cancelled' },

            checkOut: { $gte: new Date() } // Solo traer reservas futuras o actuales

        }).select('checkIn checkOut');

        

        res.status(200).json(bookings);

    } catch (error) {

        res.status(500).json({ message: 'Error al consultar disponibilidad', error: error.message });

    }

};



// 3. Disponibilidad de Experiencias (Si tienen cupo limitado por día)

export const getExperienceAvailability = async (req, res) => {

    try {

        const { id } = req.params;

        // Aquí podrías filtrar cuántos cupos quedan para una fecha específica

        res.status(200).json({ 

            message: 'Endpoint de cupos de experiencia listo para implementarse',

            experienceId: id

        });

    } catch (error) {

        res.status(500).json({ message: 'Error al consultar cupos', error: error.message });

    }

};
// 4. Limpiar caché de disponibilidad

export const clearAvailabilityCache = (req, res) => {

    // Lógica futura para limpiar Redis o memoria local

    res.status(200).json({ message: 'Caché de disponibilidad liberada' });

};
