package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "suitenights")
@CompoundIndexes({
        @CompoundIndex(name = "suite_unit_date_idx", def = "{'suite': 1, 'unitSlot': 1, 'date': 1}", unique = true)
})
public class SuiteNight {

    @Id
    private String id;

    @Indexed
    private String suite;

    @Indexed
    private String branch;

    private Integer unitSlot;
    private LocalDate date;

    @Indexed
    private String booking; // Booking ObjectId reference

    @Indexed
    private String status; // "blocked", "held", "maintenance"

    private Instant expiresAt;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
