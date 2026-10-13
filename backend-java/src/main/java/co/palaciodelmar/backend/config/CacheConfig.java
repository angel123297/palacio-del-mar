package co.palaciodelmar.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

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
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues();
    }
}
