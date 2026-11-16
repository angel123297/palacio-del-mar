package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeletedUserSecurityTest {
    @Test
    void deletedUserCannotAuthenticateOrLoadAsSession() {
        User deleted = User.builder().email("deleted@example.com").status("active").deletedAt(Instant.now()).build();
        assertFalse(new UserPrincipal(deleted).isEnabled());
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmail("deleted@example.com")).thenReturn(Optional.of(deleted));
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(repository);
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("deleted@example.com"));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserForSession("deleted@example.com"));
    }
}
