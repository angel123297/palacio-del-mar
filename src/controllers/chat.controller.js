export const getChatStatus = (req, res) => {

    res.status(200).json({ service: 'Chat', status: 'online', connections: 0 });

};



export const clearChatCache = (req, res) => {

    res.status(200).json({ message: 'Caché del chat limpiada correctamente' });

};
