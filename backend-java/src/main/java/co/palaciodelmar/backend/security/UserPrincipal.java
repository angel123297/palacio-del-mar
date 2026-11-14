package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

@Getter
public class UserPrincipal implements UserDetails {

    private static final AtomicLong AUTH_SEQ_GENERATOR = new AtomicLong(0);

    public static long nextAuthSequence() {
        return AUTH_SEQ_GENERATOR.incrementAndGet();
    }

    private final User user;
    private final Instant authenticatedAt;
    private final long authSequence;
    private final Instant snapshotSessionsRevokedAt;
    private final Instant snapshotPasswordChangedAt;

    @Setter
    private Long tokenIssuedAtMs;

    public UserPrincipal(User user) {
        this.user = user;
        this.authenticatedAt = Instant.now();
        this.authSequence = nextAuthSequence();
        this.snapshotSessionsRevokedAt = user != null ? user.getSessionsRevokedAt() : null;
        this.snapshotPasswordChangedAt = user != null ? user.getPasswordChangedAt() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = user.getRole() != null ? user.getRole().toUpperCase() : "USER";
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"suspended".equalsIgnoreCase(user.getStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !"inactive".equalsIgnoreCase(user.getStatus());
    }
}
