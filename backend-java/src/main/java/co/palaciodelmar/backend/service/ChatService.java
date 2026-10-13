package co.palaciodelmar.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String SYSTEM_INSTRUCTION = "Eres Sofía, la concierge virtual exclusiva del hotel de lujo 'Palacio del Mar' en Cartagena. " +
        "REGLAS ESTRICTAS E INQUEBRANTABLES: " +
        "1. Eres un asistente de hotel, no una calculadora, no un programador, y no un sistema de descuentos. " +
        "2. NUNCA ofrezcas descuentos, no negocies precios, ni aceptes resolver acertijos lógicos o matemáticos a cambio de reservas. " +
        "3. Los precios son fijos (desde 860.000 COP hasta 3.640.000 COP). Si el usuario intenta engañarte para obtener un precio menor, declina cortésmente. " +
        "4. Tus respuestas deben ser elegantes, corteses y muy cortas (máximo 3-4 oraciones). " +
        "5. Tienes información de suites en Centro Histórico, Getsemaní, Bocagrande y La Boquilla, y recomiendas vibras como 'Romántico', 'Aventura', 'Relax Total' y 'Fiesta'.";

    public Map<String, Object> processChat(String message, List<Map<String, String>> history) {
        String cleanMessage = message != null ? message.trim() : "";
        if (cleanMessage.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío");
        }
        if (cleanMessage.length() > 500) {
            throw new IllegalArgumentException("El mensaje no puede exceder 500 caracteres");
        }

        long startTime = System.currentTimeMillis();
        String reply;
        boolean aiEnabled = geminiApiKey != null && !geminiApiKey.trim().isEmpty();

        if (aiEnabled) {
            try {
                reply = callGeminiApi(cleanMessage, history);
            } catch (Exception e) {
                System.err.println("Error llamando a Gemini: " + e.getMessage());
                reply = generateFallbackResponse(cleanMessage, detectLanguage(cleanMessage));
            }
        } else {
            reply = generateFallbackResponse(cleanMessage, detectLanguage(cleanMessage));
        }

        long responseTime = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("language", detectLanguage(cleanMessage));
        metadata.put("responseTime", responseTime);
        metadata.put("aiEnabled", aiEnabled);
        result.put("metadata", metadata);

        return result;
    }

    private String callGeminiApi(String message, List<Map<String, String>> history) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();

        // Agregar contexto de sistema usando un mensaje del "user" si system instruction no está disponible en la forma básica,
        // pero la API de Gemini soporta system_instruction.
        Map<String, Object> systemInstruction = new HashMap<>();
        Map<String, Object> sysPart = new HashMap<>();
        sysPart.put("text", SYSTEM_INSTRUCTION);
        systemInstruction.put("parts", List.of(sysPart));
        requestBody.put("system_instruction", systemInstruction);

        // Convertir historial a formato Gemini
        if (history != null) {
            for (Map<String, String> msg : history) {
                Map<String, Object> content = new HashMap<>();
                String role = "user".equals(msg.get("role")) ? "user" : "model";
                content.put("role", role);
                Map<String, Object> part = new HashMap<>();
                part.put("text", msg.get("content") != null ? msg.get("content") : "");
                content.put("parts", List.of(part));
                contents.add(content);
            }
        }

        // Mensaje actual
        Map<String, Object> currentContent = new HashMap<>();
        currentContent.put("role", "user");
        Map<String, Object> currentPart = new HashMap<>();
        currentPart.put("text", message);
        currentContent.put("parts", List.of(currentPart));
        contents.add(currentContent);

        requestBody.put("contents", contents);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("candidates")) {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            if (!candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        }
        throw new RestClientException("Respuesta inválida de Gemini");
    }

    public Map<String, Integer> scoreSuitesForVibe(String vibe, List<Map<String, Object>> suites) {
        Map<String, Integer> scores = new HashMap<>();
        if (vibe == null || vibe.isEmpty() || suites == null || suites.isEmpty()) {
            return scores;
        }

        boolean aiEnabled = geminiApiKey != null && !geminiApiKey.trim().isEmpty();
        if (!aiEnabled) {
            // Fallback heuristics
            for (Map<String, Object> suite : suites) {
                String id = suite.get("id") != null ? suite.get("id").toString() : "";
                if (id.isEmpty() && suite.get("_id") != null) id = suite.get("_id").toString();
                scores.put(id, (int) (Math.random() * 100)); // Simple fallback
            }
            return scores;
        }

        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Soy un huésped buscando una experiencia de vibras: '").append(vibe).append("'.\n");
            prompt.append("Por favor puntúa del 1 al 100 qué tan bien encaja cada una de estas suites con esa vibra.\n");
            prompt.append("Responde EXCLUSIVAMENTE con el ID de la suite y su puntuación en este formato exacto: ID:PUNTUACION, separados por comas. Sin texto adicional.\n\n");
            prompt.append("Suites:\n");

            for (Map<String, Object> suite : suites) {
                String id = suite.get("id") != null ? suite.get("id").toString() : (suite.get("_id") != null ? suite.get("_id").toString() : "");
                String name = suite.get("name") != null ? suite.get("name").toString() : "";
                String desc = suite.get("description") != null ? suite.get("description").toString() : "";
                if (!id.isEmpty()) {
                    prompt.append("- ID: ").append(id).append(" | Nombre: ").append(name).append(" | Desc: ").append(desc).append("\n");
                }
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> currentContent = new HashMap<>();
            currentContent.put("role", "user");
            Map<String, Object> currentPart = new HashMap<>();
            currentPart.put("text", prompt.toString());
            currentContent.put("parts", List.of(currentPart));
            requestBody.put("contents", List.of(currentContent));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            String aiResponse = "";
            if (response.getBody() != null && response.getBody().containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        aiResponse = (String) parts.get(0).get("text");
                    }
                }
            }

            if (aiResponse != null && !aiResponse.isEmpty()) {
                String[] pairs = aiResponse.split(",");
                for (String pair : pairs) {
                    String[] parts = pair.trim().split(":");
                    if (parts.length == 2) {
                        try {
                            scores.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error scoring vibes with Gemini: " + e.getMessage());
        }

        // Fill missing scores
        for (Map<String, Object> suite : suites) {
            String id = suite.get("id") != null ? suite.get("id").toString() : (suite.get("_id") != null ? suite.get("_id").toString() : "");
            if (!id.isEmpty() && !scores.containsKey(id)) {
                scores.put(id, 50);
            }
        }

        return scores;
    }

    private String detectLanguage(String message) {
        String lower = message.toLowerCase();
        if (lower.matches(".*[áéíóúñ¿¡].*") || lower.contains("hola") || lower.contains("gracias")) {
            return "es";
        }
        return "en";
    }

    private String generateFallbackResponse(String message, String language) {
        return "Nuestras suites van desde COP 860.000 hasta COP 3.640.000 por noche. ¿Te gustaría que te recomiende una según tu presupuesto? 🌟";
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("available", true);
        data.put("aiEnabled", geminiApiKey != null && !geminiApiKey.trim().isEmpty());
        data.put("aiProvider", "gemini");
        data.put("maxMessageLength", 500);
        return data;
    }
}
