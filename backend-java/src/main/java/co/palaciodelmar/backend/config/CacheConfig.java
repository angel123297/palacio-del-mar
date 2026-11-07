package co.palaciodelmar.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Falla de lectura en caché '{}' para clave '{}': {}. Continuando con consulta a base de datos.",
                        cache != null ? cache.getName() : "desconocida", key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Falla de escritura en caché '{}' para clave '{}': {}. Omitiendo caché.",
                        cache != null ? cache.getName() : "desconocida", key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Falla al invalidar caché '{}' para clave '{}': {}.",
                        cache != null ? cache.getName() : "desconocida", key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Falla al vaciar caché '{}': {}.",
                        cache != null ? cache.getName() : "desconocida", exception.getMessage());
            }
        };
    }

    @Bean
    @ConditionalOnClass(RedisCacheConfiguration.class)
    public RedisCacheConfiguration defaultRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("palacio:cache:")
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues();
    }

    /**
     * Pilar 6 Redis: Gestor de caché multi-TTL especializado por volatilidad de dominio.
     * Solo se activa cuando spring.cache.type=redis (producción/Docker), preservando el caché
     * simple en memoria para suites de pruebas sin servidor Redis.
     */
    @Bean
    @ConditionalOnClass(RedisCacheManager.class)
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = defaultRedisCacheConfiguration();

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        perCacheConfig.put("availability_monthly", defaults.entryTtl(Duration.ofMinutes(3)));
        perCacheConfig.put("availability_search", defaults.entryTtl(Duration.ofMinutes(2)));
        perCacheConfig.put("dynamic_pricing", defaults.entryTtl(Duration.ofMinutes(3)));
        perCacheConfig.put("suites", defaults.entryTtl(Duration.ofMinutes(15)));
        perCacheConfig.put("suite_detail", defaults.entryTtl(Duration.ofMinutes(15)));
        perCacheConfig.put("suite_types", defaults.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCacheConfig)
                .transactionAware()
                .build();
    }
}
