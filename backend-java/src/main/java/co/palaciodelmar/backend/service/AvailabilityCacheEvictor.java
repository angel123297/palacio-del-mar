package co.palaciodelmar.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pilar 6 Redis: Invalidador centralizado y tolerante a fallos para los cachés de disponibilidad
 * y tarifas dinámicas cuando ocurre una reserva, modificación, cancelación, expiración o cambio de catálogo.
 */
@Slf4j
@Service
public class AvailabilityCacheEvictor {

    public static final List<String> AVAILABILITY_CACHES = List.of(
            "availability_monthly",
            "availability_search",
            "dynamic_pricing"
    );

    public static final List<String> CATALOG_CACHES = List.of(
            "suites",
            "suite_detail",
            "suite_types"
    );

    private final CacheManager cacheManager;

    @Autowired
    public AvailabilityCacheEvictor(@Autowired(required = false) CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAvailability() {
        clearCaches(AVAILABILITY_CACHES);
    }

    public void evictAll() {
        clearCaches(AVAILABILITY_CACHES);
        clearCaches(CATALOG_CACHES);
    }

    private void clearCaches(List<String> cacheNames) {
        if (cacheManager == null) {
            return;
        }
        for (String name : cacheNames) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            } catch (Exception e) {
                log.warn("No se pudo invalidar el caché '{}': {}", name, e.getMessage());
            }
        }
    }
}
