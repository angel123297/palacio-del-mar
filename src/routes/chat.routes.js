import { Router } from 'express';

import { getChatStatus, clearChatCache } from '../controllers/chat.controller.js';



const router = Router();



router.get('/status', getChatStatus);

router.delete('/clear-cache', clearChatCache); // Usamos DELETE por ser una acción de borrado



export default router;
