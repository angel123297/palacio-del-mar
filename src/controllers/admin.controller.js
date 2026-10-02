import User from '../models/User.js';



export const getUserById = async (req, res) => {

    try {

        const user = await User.findById(req.params.id).select('-password'); // Ocultamos la contraseña

        if (!user) return res.status(404).json({ message: 'Usuario no encontrado' });

        res.status(200).json(user);

    } catch (error) {

        res.status(500).json({ message: 'Error al consultar el usuario', error: error.message });

    }

};
