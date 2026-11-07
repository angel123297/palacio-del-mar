package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserSessionCacheService userSessionCacheService;

    @Autowired
    public UserDetailsServiceImpl(
            UserRepository userRepository,
            @Autowired(required = false) UserSessionCacheService userSessionCacheService
    ) {
        this.userRepository = userRepository;
        this.userSessionCacheService = userSessionCacheService;
    }

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this(userRepository, null);
    }

    public UserDetails loadUserForSession(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email vacío");
        }
        String normalizedEmail = email.trim().toLowerCase();

        if (userSessionCacheService != null) {
            Optional<User> cached = userSessionCacheService.getCachedUser(normalizedEmail);
            if (cached.isPresent()) {
                return new UserPrincipal(cached.get());
            }
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        if (userSessionCacheService != null) {
            userSessionCacheService.putCachedUser(user);
            return new UserPrincipal(UserSessionCacheService.sanitizeForCache(user));
        }
        return new UserPrincipal(user);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email vacío");
        }
        String normalizedEmail = email.trim().toLowerCase();

        // DaoAuthenticationProvider invoca loadUserByUsername durante POST /api/auth/login
        // y necesita el hash BCrypt desde MongoDB (ya que Redis almacena copias sanitizadas sin password).
        if (userSessionCacheService != null) {
            Optional<User> cached = userSessionCacheService.getCachedUser(normalizedEmail);
            if (cached.isPresent() && cached.get().getPassword() != null) {
                return new UserPrincipal(cached.get());
            }
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        if (userSessionCacheService != null) {
            userSessionCacheService.putCachedUser(user);
        }
        return new UserPrincipal(user);
    }
}
