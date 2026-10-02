import Booking from '../models/Booking.js';



export const confirmBooking = async (req, res) => {

    try {

        const booking = await Booking.findByIdAndUpdate(

            req.params.id, 

            { status: 'confirmed' },

            { new: true }

        );

        

        if (!booking) return res.status(404).json({ message: 'Reserva no encontrada' });



        // Aquí iría la lógica para enviar el correo con el PDF al cliente

        res.status(200).json({ 

            message: 'Reserva confirmada y correo enviado exitosamente', 

            booking 

        });

    } catch (error) {

        res.status(500).json({ message: 'Error al confirmar la reserva', error: error.message });

    }

};
