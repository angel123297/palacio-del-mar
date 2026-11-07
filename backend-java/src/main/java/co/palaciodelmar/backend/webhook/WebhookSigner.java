package co.palaciodelmar.backend.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class WebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private WebhookSigner() {}

    public static String sign(String payload, String secret) {
        if (payload == null || secret == null) {
            throw new IllegalArgumentException("Payload y secret son obligatorios para firmar");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar la firma HMAC-SHA256", e);
        }
    }

    public static boolean verify(String payload, String secret, String expectedSignature) {
        if (payload == null || secret == null || expectedSignature == null || expectedSignature.isBlank()) {
            return false;
        }
        try {
            String calculated = sign(payload, secret);
            return MessageDigest.isEqual(
                    calculated.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    expectedSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
