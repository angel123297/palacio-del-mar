package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class WhatsAppService {

    @Value("${whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${whatsapp.api.token:}")
    private String apiToken;

    @Value("${whatsapp.phone.number.id:}")
    private String phoneNumberId;

    private final RestClient restClient = RestClient.create();

    /**
     * Envía confirmación de reserva al WhatsApp del huésped.
     * En entorno local (enabled=false), simula el envío con formato de lujo en los logs.
     */
    @Async
    public CompletableFuture<Boolean> sendBookingConfirmation(Booking booking, User guest, Suite suite) {
        String phone = null;
        if (guest != null && guest.getProfile() != null && guest.getProfile().getPhone() != null) {
            phone = guest.getProfile().getPhone();
        } else if (booking != null && booking.getGuestDetails() != null) {
            phone = booking.getGuestDetails().getPhone();
        }

        String guestName = guest != null && guest.getName() != null ? guest.getName() :
                (booking != null && booking.getGuestDetails() != null && booking.getGuestDetails().getFullName() != null
                        ? booking.getGuestDetails().getFullName() : "Estimado Huésped");

        String suiteTitle = suite != null && suite.getName() != null ? suite.getName() : "Suite de Lujo";

        String messageText = String.format(
                "🏰 *PALACIO DEL MAR — CARTAGENA*\n\n" +
                "¡Hola %s! Tu reserva ha sido confirmada con éxito. ✨\n\n" +
                "📌 *Detalles de tu estadía:*\n" +
                "• *Suite:* %s\n" +
                "• *Check-in:* %s (a partir de las 3:00 PM)\n" +
                "• *Check-out:* %s (hasta las 11:00 AM)\n" +
                "• *Código de Reserva:* `%s`\n" +
                "• *Código de Acceso Smart Lock:* `%s`\n\n" +
                "📍 *Cómo llegar:* https://maps.google.com/?q=Cartagena+Centro+Historico\n\n" +
                "Si necesitas transporte privado o concierge, solo responde a este mensaje.\n" +
                "¡Esperamos hacer de tu viaje una experiencia inolvidable! 🌴",
                guestName,
                suiteTitle,
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getId(),
                booking.getSmartLockPin() != null ? booking.getSmartLockPin() : "PENDIENTE"
        );

        if (!enabled || apiToken.isBlank() || phoneNumberId.isBlank()) {
            log.info("\n=======================================================\n" +
                     "📱 [SIMULADOR WHATSAPP BUSINESS - MODO DESARROLLO]\n" +
                     "Destinatario: {}\n" +
                     "Mensaje:\n{}\n" +
                     "=======================================================",
                    phone != null ? phone : "+57 300 000 0000", messageText);
            return CompletableFuture.completedFuture(true);
        }

        try {
            // Meta Cloud API oficial
            String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";
            
            Map<String, Object> body = new HashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", phone);
            body.put("type", "text");
            body.put("text", Map.of("preview_url", true, "body", messageText));

            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Mensaje oficial de WhatsApp enviado a {}", phone);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Error al enviar mensaje por WhatsApp Cloud API: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}
