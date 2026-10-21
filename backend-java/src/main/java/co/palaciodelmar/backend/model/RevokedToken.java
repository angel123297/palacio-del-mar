package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Entidad para almacenamiento duradero de tokens revocados (blacklist).
 * Posee un índice TTL en expiresAt para que MongoDB purgue automáticamente
 * los tokens cuando su vigencia natural expira.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "revoked_tokens")
public class RevokedToken {

    @Id
    private String tokenHash;

    @Indexed(expireAfter = "0s")
    private Date expiresAt;

    private Date revokedAt;
}
