export const resendVerification = async (req, res) => {

    try {

        const { email } = req.body;

        // Aquí irá la lógica de Nodemailer o Resend para enviar el correo

        res.status(200).json({ message: `Correo de verificación reenviado a ${email}` });

    } catch (error) {

        res.status(500).json({ message: 'Error al reenviar el correo', error: error.message });

    }

};
