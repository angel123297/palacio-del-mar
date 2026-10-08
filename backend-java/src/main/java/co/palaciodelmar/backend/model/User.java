package co.palaciodelmar.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @JsonProperty("_id")
    public String get_id() {
        return id;
    }

    private String name;
    private String lastName;

    @Indexed(unique = true)
    private String email;

    private String password;

    @Builder.Default
    private String role = "user"; // "user", "admin", "host"

    @Builder.Default
    private List<String> branches = new ArrayList<>();

    @Builder.Default
    private String status = "active"; // "active", "inactive", "suspended", "pending_verification"

    @Builder.Default
    private Boolean emailVerified = true;

    private Profile profile;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant deletedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        private String phone;
        private Address address;
        private String documentId;
        private String documentType; // "cedula", "passport", etc.
        private String avatar;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String country;
        private String postalCode;
    }
}
