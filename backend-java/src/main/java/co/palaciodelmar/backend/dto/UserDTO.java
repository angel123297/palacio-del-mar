package co.palaciodelmar.backend.dto;

import co.palaciodelmar.backend.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String _id;
    private String name;
    private String lastName;
    private String email;
    private String role;
    private List<String> branches;
    private String status;
    private Boolean emailVerified;
    private User.Profile profile;
    private Instant createdAt;
    private Instant updatedAt;
}
