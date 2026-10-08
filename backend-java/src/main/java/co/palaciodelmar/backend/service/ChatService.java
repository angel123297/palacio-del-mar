package co.palaciodelmar.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    public Map<String, Object> processChat(String message, List<Map<String, String>> history) {
        String cleanMessage = message != null ? message.trim() : "";
        if (cleanMessage.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío");
        }
        if (cleanMessage.length() > 500) {
            throw new IllegalArgumentException("El mensaje no puede exceder 500 caracteres");
        }

        String language = detectLanguage(cleanMessage);
        String reply = generateFallbackResponse(cleanMessage, language);

        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("language", language);
        metadata.put("responseTime", 15);
        result.put("metadata", metadata);

        return result;
    }

    private String detectLanguage(String message) {
        String lower = message.toLowerCase();
        if (lower.matches(".*[áéíóúñ¿¡]|hola|gracias|por favor|qué|cómo|cuándo|dónde|quién.*")) {
            return "es";
        }
        return "en";
    }

    private String generateFallbackResponse(String message, String language) {
        String lowerMsg = message.toLowerCase();

        if ("es".equals(language)) {
            if (lowerMsg.contains("precio") || lowerMsg.contains("cuesta") || lowerMsg.contains("tarifa")) {
                return "Nuestras suites van desde COP 860.000 hasta COP 3.640.000 por noche. ¿Te gustaría que te recomiende una según tu presupuesto? 🌟";
            }
            if (lowerMsg.contains("suite") || lowerMsg.contains("habitación") || lowerMsg.contains("habitacion")) {
                return "Tenemos suites exclusivas en nuestras sucursales de Centro Histórico, Getsemaní, Bocagrande y La Boquilla. ¿Cuál te llama más la atención?";
            }
            if (lowerMsg.contains("experiencia") || lowerMsg.contains("actividad") || lowerMsg.contains("excursión")) {
                return "Ofrecemos experiencias únicas: navegar a las Islas del Rosario ⛵, atardecer en la Muralla 🏛️, ruta gastronómica con nuestro chef 🌿 y noche cultural palenquera 🎶. ¿Te interesa alguna?";
            }
            if (lowerMsg.contains("disponibilidad") || lowerMsg.contains("reservar")) {
                return "Puedes consultar disponibilidad y precios usando nuestro buscador de reservas en esta misma página. ¡Reservando directo obtienes desayuno incluido y upgrade gratuito! ✨";
            }
            if (lowerMsg.contains("spa") || lowerMsg.contains("masaje") || lowerMsg.contains("bienestar")) {
                return "Nuestro spa ofrece masajes terapéuticos, rituales con cacao ancestral y yoga al amanecer. ¿Te gustaría que te recomiende un tratamiento en particular? 🧘";
            }
            if (lowerMsg.contains("restaurante") || lowerMsg.contains("comida") || lowerMsg.contains("gastronom")) {
                return "Contamos con alta cocina caribeña, desayunos artesanales y nuestro Rooftop Bar con vistas 360° de la ciudad amurallada. 🍽️";
            }
            if (lowerMsg.contains("hola") || lowerMsg.contains("buenas") || lowerMsg.contains("saludo")) {
                return "¡Hola! Soy Sofía, tu concierge virtual. ¿En qué puedo ayudarte a planear tu estadía en Cartagena? 🌊✨";
            }
            if (lowerMsg.contains("gracias")) {
                return "¡Por nada! Es un placer ayudarte. ¿Hay algo más en lo que pueda asistirte? 😊";
            }
            return "Con gusto te ayudo. ¿Te gustaría conocer nuestras suites, experiencias, precios o hacer una reserva? Estoy aquí para asistirte en todo lo que necesites. 🌟";
        }

        if (lowerMsg.contains("price") || lowerMsg.contains("cost") || lowerMsg.contains("rate")) {
            return "Our suites range from COP 860,000 to COP 3,640,000 per night. Would you like me to recommend one based on your budget? 🌟";
        }
        if (lowerMsg.contains("suite") || lowerMsg.contains("room")) {
            return "We have exclusive suites across our historic and beach branches in Cartagena. Which one catches your attention?";
        }
        if (lowerMsg.contains("experience") || lowerMsg.contains("activity") || lowerMsg.contains("tour")) {
            return "We offer unique experiences: sailing to Rosario Islands ⛵, sunset at the Walled City 🏛️, gastronomic tour with our chef 🌿, and Palenque cultural night 🎶. Interested in any?";
        }
        if (lowerMsg.contains("availability") || lowerMsg.contains("book")) {
            return "You can check availability and prices using our booking widget on this page. Booking directly gets you breakfast included and a free upgrade! ✨";
        }
        if (lowerMsg.contains("hello") || lowerMsg.contains("hi") || lowerMsg.contains("hey")) {
            return "Hello! I'm Sofía, your virtual concierge. How can I help you plan your stay in Cartagena? 🌊✨";
        }
        if (lowerMsg.contains("thank")) {
            return "You're welcome! It's my pleasure to help. Is there anything else I can assist you with? 😊";
        }
        return "I'd be happy to help. Would you like to learn about our suites, experiences, prices, or make a reservation? I'm here to assist you with everything you need. 🌟";
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("available", true);
        data.put("aiEnabled", false);
        data.put("aiProvider", "offline");
        data.put("maxMessageLength", 500);
        return data;
    }
}
