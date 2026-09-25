import axios from 'axios';

// ============================================
// CONFIGURACIÓN
// ============================================

// Límites y configuraciones
const MAX_HISTORY_LENGTH = 10;
const MAX_MESSAGE_LENGTH = 500;
const RATE_LIMIT_WINDOW = 60000; // 1 minuto
const RATE_LIMIT_MAX = 10; // 10 mensajes por minuto

// Almacenamiento en memoria para rate limiting (en producción usar Redis)
const userRequestCounts = new Map();

// ============================================
// CONTEXTO DEL HOTEL (MEJORADO)
// ============================================

const HOTEL_CONTEXT = `
Eres Sofía, la concierge virtual del Hotel Palacio del Mar, un hotel boutique 5 estrellas ubicado en la Ciudad Amurallada de Cartagena de Indias, Colombia.

INFORMACIÓN COMPLETA DEL HOTEL:

🏨 SUITES Y PRECIOS (COP - pesos colombianos):
- Superior Patio: desde $860.000/noche (42m², jardín privado)
- Suite Colonial: desde $1.310.000/noche (65m², patio colonial, tina hidromasaje)
- Suite Bahía: desde $1.720.000/noche (85m², vista al mar, jacuzzi, balcón)
- Suite Presidencial: desde $2.780.000/noche (180m², terraza privada, jacuzzi, mayordomo)
- Penthouse Muralla: desde $3.640.000/noche (220m², vista 360°, piscina privada)

✨ EXPERIENCIAS:
- Islas del Rosario: $348.000/persona (navegación privada, snorkel, almuerzo)
- Atardecer en la Muralla: $184.000/persona (cóctel, vista al Caribe, ron artesanal)
- Ruta del Sabor: $266.000/persona (tour gastronómico, cocina en vivo)
- Noche de Palenque: $389.000/persona (música, baile, cena incluida)

🍽️ GASTRONOMÍA:
- El Gobernador: alta cocina caribeña, terraza colonial
- Bistró del Patio: desayunos artesanales, frutas tropicales
- Rooftop Bar: cócteles de autor, vistas 360°, exclusivo para huéspedes

💆 SPA Y BIENESTAR:
- Masajes terapéuticos
- Rituales con cacao ancestral
- Terapia marina
- Yoga al amanecer
- Hidroterapia
- Meditación guiada

⭐ BENEFICIOS RESERVA DIRECTA:
- Mejor precio garantizado (hasta 25% menos que OTAs)
- Desayuno incluido para 2 personas
- Check-in anticipado (sin cargo)
- Copa de bienvenida
- Cancelación flexible sin penalidad
- Upgrade de habitación sujeto a disponibilidad

📍 UBICACIÓN:
- Calle del Curato #35-12, Ciudad Amurallada
- 2 minutos a pie de la Plaza de Bolívar
- 15 minutos del Aeropuerto Rafael Núñez
- Cerca de boutiques, galerías y mercados

📞 CONTACTO:
- Email: reservas@palaciomar.co
- Teléfono: +57 (5) 660 0000
- WhatsApp: +57 300 000 0000

INSTRUCCIONES PARA TI (SOFÍA):
1. Responde SIEMPRE en el mismo idioma del usuario (español o inglés)
2. Sé cálida, elegante y servicial como una concierge de lujo
3. Respuestas breves (máximo 3-4 oraciones), conversacionales
4. Anima al usuario a usar el formulario de reservas en la web
5. Si preguntan por disponibilidad, sugiere usar el buscador de la página
6. Si no sabes algo específico, ofrece conectar con el equipo humano via WhatsApp
7. Menciona los beneficios de reservar directo cuando sea relevante
8. Sé entusiasta y muestra pasión por Cartagena y el hotel
`;

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Detecta el idioma del mensaje
 */
const detectLanguage = (message) => {
  const spanishPatterns = /[áéíóúñ¿¡]|hola|gracias|por favor|qué|cómo|cuándo|dónde|quién/i;
  return spanishPatterns.test(message) ? 'es' : 'en';
};

/**
 * Limpia y valida el mensaje
 */
const validateMessage = (message) => {
  if (!message || typeof message !== 'string') {
    throw new Error('Mensaje inválido');
  }
  
  const trimmed = message.trim();
  if (trimmed.length === 0) {
    throw new Error('El mensaje no puede estar vacío');
  }
  
  if (trimmed.length > MAX_MESSAGE_LENGTH) {
    throw new Error(`El mensaje no puede exceder ${MAX_MESSAGE_LENGTH} caracteres`);
  }
  
  return trimmed;
};

/**
 * Filtra y sanea el `history` que manda el cliente antes de reenviarlo a la
 * API de IA (SEC-019). Sin esto, un cliente podía mandar hasta
 * MAX_HISTORY_LENGTH elementos con cualquier `role` (incluido 'system', para
 * intentar sobrescribir las instrucciones de Sofía) y con `content` de varios
 * megabytes cada uno, ya que solo se limitaba la CANTIDAD de mensajes, nunca
 * su forma ni su tamaño. Aquí:
 * - se descarta cualquier elemento que no sea `{ role, content }`
 * - solo se permite role 'user' o 'assistant' (nunca 'system' ni otro valor)
 * - `content` debe ser string, y se recorta al mismo límite que el mensaje
 *   actual (MAX_MESSAGE_LENGTH)
 * - cualquier otro campo del objeto se descarta
 */
const sanitizeHistory = (history) => {
  if (!Array.isArray(history)) return [];

  return history
    .filter(item => (
      item &&
      typeof item === 'object' &&
      (item.role === 'user' || item.role === 'assistant') &&
      typeof item.content === 'string' &&
      item.content.trim().length > 0
    ))
    .slice(-MAX_HISTORY_LENGTH)
    .map(item => ({
      role: item.role,
      content: item.content.trim().slice(0, MAX_MESSAGE_LENGTH)
    }));
};

/**
 * Rate limiting por usuario/IP
 */
const checkRateLimit = (identifier) => {
  const now = Date.now();
  const userData = userRequestCounts.get(identifier) || { count: 0, resetTime: now + RATE_LIMIT_WINDOW };
  
  if (now > userData.resetTime) {
    userData.count = 1;
    userData.resetTime = now + RATE_LIMIT_WINDOW;
  } else {
    userData.count++;
  }
  
  userRequestCounts.set(identifier, userData);
  
  const remaining = Math.max(0, RATE_LIMIT_MAX - userData.count);
  const resetIn = Math.ceil((userData.resetTime - now) / 1000);
  
  return {
    allowed: userData.count <= RATE_LIMIT_MAX,
    remaining,
    resetIn
  };
};

/**
 * Genera respuesta simulada (fallback cuando la API no está disponible)
 */
const generateFallbackResponse = (message, language) => {
  const lowerMsg = message.toLowerCase();
  
  // Respuestas en español
  if (language === 'es') {
    if (lowerMsg.includes('precio') || lowerMsg.includes('cuesta') || lowerMsg.includes('tarifa')) {
      return `Nuestras suites van desde COP 860.000 hasta COP 3.640.000 por noche. ¿Te gustaría que te recomiende una según tu presupuesto? 🌟`;
    }
    if (lowerMsg.includes('suite') || lowerMsg.includes('habitación')) {
      return `Tenemos 5 tipos de suites: Superior Patio (acogedora), Suite Colonial (histórica), Suite Bahía (vistas al mar), Presidencial (lujo total) y Penthouse Muralla (exclusiva). ¿Cuál te llama más la atención?`;
    }
    if (lowerMsg.includes('experiencia') || lowerMsg.includes('actividad') || lowerMsg.includes('excursión')) {
      return `Ofrecemos experiencias únicas: navegar a las Islas del Rosario ⛵, atardecer en la Muralla 🏛️, ruta gastronómica con nuestro chef 🌿 y noche cultural palenquera 🎶. ¿Te interesa alguna?`;
    }
    if (lowerMsg.includes('disponibilidad') || lowerMsg.includes('reservar')) {
      return `Puedes consultar disponibilidad y precios usando nuestro buscador de reservas en esta misma página. ¡Reservando directo obtienes desayuno incluido y upgrade gratuito! ✨`;
    }
    if (lowerMsg.includes('spa') || lowerMsg.includes('masaje') || lowerMsg.includes('bienestar')) {
      return `Nuestro spa ofrece masajes terapéuticos, rituales con cacao ancestral y yoga al amanecer. ¿Te gustaría que te recomiende un tratamiento en particular? 🧘`;
    }
    if (lowerMsg.includes('restaurante') || lowerMsg.includes('comida') || lowerMsg.includes('gastronom')) {
      return `Contamos con El Gobernador (alta cocina caribeña), Bistró del Patio (desayunos artesanales) y nuestro Rooftop Bar con vistas 360° de la ciudad amurallada. 🍽️`;
    }
    if (lowerMsg.includes('hola') || lowerMsg.includes('buenas') || lowerMsg.includes('saludo')) {
      return `¡Hola! Soy Sofía, tu concierge virtual. ¿En qué puedo ayudarte a planear tu estadía en Cartagena? 🌊✨`;
    }
    if (lowerMsg.includes('gracias')) {
      return `¡Por nada! Es un placer ayudarte. ¿Hay algo más en lo que pueda asistirte? 😊`;
    }
    
    return `Con gusto te ayudo. ¿Te gustaría conocer nuestras suites, experiencias, precios o hacer una reserva? Estoy aquí para asistirte en todo lo que necesites. 🌟`;
  }
  
  // Respuestas en inglés
  if (lowerMsg.includes('price') || lowerMsg.includes('cost') || lowerMsg.includes('rate')) {
    return `Our suites range from COP 860,000 to COP 3,640,000 per night. Would you like me to recommend one based on your budget? 🌟`;
  }
  if (lowerMsg.includes('suite') || lowerMsg.includes('room')) {
    return `We have 5 suite types: Superior Patio (cozy), Colonial Suite (historic), Bahía Suite (ocean views), Presidential Suite (luxury), and Muralla Penthouse (exclusive). Which one catches your attention?`;
  }
  if (lowerMsg.includes('experience') || lowerMsg.includes('activity') || lowerMsg.includes('tour')) {
    return `We offer unique experiences: sailing to Rosario Islands ⛵, sunset at the Walled City 🏛️, gastronomic tour with our chef 🌿, and Palenque cultural night 🎶. Interested in any?`;
  }
  if (lowerMsg.includes('availability') || lowerMsg.includes('book')) {
    return `You can check availability and prices using our booking widget on this page. Booking directly gets you breakfast included and a free upgrade! ✨`;
  }
  if (lowerMsg.includes('spa') || lowerMsg.includes('massage') || lowerMsg.includes('wellness')) {
    return `Our spa offers therapeutic massages, cacao rituals, and sunrise yoga. Would you like me to recommend a specific treatment? 🧘`;
  }
  if (lowerMsg.includes('restaurant') || lowerMsg.includes('food') || lowerMsg.includes('dining')) {
    return `We have El Gobernador (high Caribbean cuisine), Bistró del Patio (artisanal breakfasts), and our Rooftop Bar with 360° views of the Walled City. 🍽️`;
  }
  if (lowerMsg.includes('hello') || lowerMsg.includes('hi') || lowerMsg.includes('hey')) {
    return `Hello! I'm Sofía, your virtual concierge. How can I help you plan your stay in Cartagena? 🌊✨`;
  }
  if (lowerMsg.includes('thank')) {
    return `You're welcome! It's my pleasure to help. Is there anything else I can assist you with? 😊`;
  }
  
  return `I'd be happy to help. Would you like to learn about our suites, experiences, prices, or make a reservation? I'm here to assist you with everything you need. 🌟`;
};

/**
 * Llama a la API de Claude (o GPT)
 */
const callAIAPI = async (message, history, language) => {
  // Si no hay API key, usar respuestas simuladas
  if (!process.env.ANTHROPIC_API_KEY && !process.env.OPENAI_API_KEY) {
    console.log('[Chat] Usando modo offline (sin API key)');
    return generateFallbackResponse(message, language);
  }
  
  try {
    // Intentar primero con Claude (Anthropic)
    if (process.env.ANTHROPIC_API_KEY) {
      const response = await axios.post(
        'https://api.anthropic.com/v1/messages',
        {
          model: 'claude-sonnet-5',
          max_tokens: 300,
          system: HOTEL_CONTEXT,
          messages: [
            ...history,
            { role: 'user', content: message }
          ]
        },
        {
          headers: {
            'Content-Type': 'application/json',
            'x-api-key': process.env.ANTHROPIC_API_KEY,
            'anthropic-version': '2023-06-01'
          },
          timeout: 10000 // 10 segundos
        }
      );
      
      if (response.data?.content?.[0]?.text) {
        return response.data.content[0].text;
      }
    }
    
    // Fallback a OpenAI GPT
    if (process.env.OPENAI_API_KEY) {
      const response = await axios.post(
        'https://api.openai.com/v1/chat/completions',
        {
          model: 'gpt-3.5-turbo',
          messages: [
            { role: 'system', content: HOTEL_CONTEXT },
            ...history,
            { role: 'user', content: message }
          ],
          max_tokens: 300,
          temperature: 0.7
        },
        {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`
          },
          timeout: 10000
        }
      );
      
      if (response.data?.choices?.[0]?.message?.content) {
        return response.data.choices[0].message.content;
      }
    }
    
    // Si todo falla, usar respuesta simulada
    return generateFallbackResponse(message, language);
    
  } catch (error) {
    // Antes solo se usaba el modo offline si el error era de timeout o de
    // DNS (ECONNABORTED/ENOTFOUND); cualquier otro fallo (API key
    // inválida, modelo no encontrado, la API saturada, límite de cuota,
    // etc.) se relanzaba y el chat completo devolvía un 500 al huésped.
    // El chat es una comodidad, no algo crítico: ante CUALQUIER error de
    // la API de IA, degradamos a las respuestas predefinidas en vez de
    // romper la conversación.
    console.error('[AI API Error]:', error.response?.data || error.message);
    return generateFallbackResponse(message, language);
  }
};

// ============================================
// MAIN CONTROLLER
// ============================================

/**
 * @desc    Chat con IA (Claude/GPT)
 * @route   POST /api/chat
 * @access  Public
 */
export const chatWithAI = async (req, res) => {
  const startTime = Date.now();
  const clientId = req.ip || req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
  
  try {
    const { message, history = [] } = req.body;
    
    // 1. Validar mensaje
    const cleanMessage = validateMessage(message);
    
    // 2. Rate limiting
    const rateLimit = checkRateLimit(clientId);
    if (!rateLimit.allowed) {
      return res.status(429).json({
        success: false,
        message: 'Demasiadas solicitudes. Por favor espera un momento.',
        retryAfter: rateLimit.resetIn
      });
    }
    
    // 3. Detectar idioma
    const language = detectLanguage(cleanMessage);
    
    // 4. Validar historial (SEC-019): filtra a { role: user|assistant, content }
    // y acota el tamaño de cada content, nunca confiar en el array tal cual llega
    const validHistory = sanitizeHistory(history);
    
    // 5. Obtener respuesta de IA
    const reply = await callAIAPI(cleanMessage, validHistory, language);
    
    // 6. Logging
    const responseTime = Date.now() - startTime;
    console.log(`[Chat] ${clientId} | ${responseTime}ms | ${language} | "${cleanMessage.substring(0, 50)}..."`);
    
    // 7. Responder
    res.json({
      success: true,
      reply,
      metadata: {
        language,
        responseTime,
        rateLimit: {
          remaining: rateLimit.remaining,
          resetIn: rateLimit.resetIn
        }
      }
    });
    
  } catch (error) {
    console.error('[Chat Error]:', {
      clientId,
      error: error.message,
      stack: process.env.NODE_ENV === 'development' ? error.stack : undefined
    });
    
    // Manejo específico de errores
    if (error.message === 'Mensaje inválido' || error.message === 'El mensaje no puede estar vacío') {
      return res.status(400).json({
        success: false,
        message: error.message
      });
    }
    
    if (error.message.includes('exceder')) {
      return res.status(400).json({
        success: false,
        message: error.message
      });
    }
    
    res.status(500).json({
      success: false,
      message: 'Error procesando el mensaje. Por favor intenta de nuevo.',
      fallback: generateFallbackResponse(req.body?.message || '', 'es')
    });
  }
};

/**
 * @desc    Obtener estado del chat (si está disponible)
 * @route   GET /api/chat/status
 * @access  Public
 */
export const getChatStatus = async (req, res) => {
  const hasAnthropic = !!process.env.ANTHROPIC_API_KEY;
  const hasOpenAI = !!process.env.OPENAI_API_KEY;
  const hasAI = hasAnthropic || hasOpenAI;
  
  res.json({
    success: true,
    data: {
      available: true,
      aiEnabled: hasAI,
      aiProvider: hasAnthropic ? 'claude' : (hasOpenAI ? 'openai' : 'offline'),
      maxMessageLength: MAX_MESSAGE_LENGTH,
      rateLimit: {
        maxRequests: RATE_LIMIT_MAX,
        windowSeconds: RATE_LIMIT_WINDOW / 1000
      }
    }
  });
};

/**
 * @desc    Limpiar cache de rate limiting (admin)
 * @route   POST /api/chat/clear-cache
 * @access  Private (Admin)
 */
export const clearRateLimitCache = async (req, res) => {
  // Solo administradores pueden acceder
  if (req.user?.role !== 'admin') {
    return res.status(403).json({
      success: false,
      message: 'Acceso denegado'
    });
  }
  
  userRequestCounts.clear();
  
  res.json({
    success: true,
    message: 'Cache de rate limiting limpiado'
  });
};