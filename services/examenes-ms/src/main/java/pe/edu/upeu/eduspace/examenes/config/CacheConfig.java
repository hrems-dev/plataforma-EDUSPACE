package pe.edu.upeu.eduspace.examenes.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String CACHE_ASIGNACIONES = "asignacionesDocenteCache";
    public static final String CACHE_CURSOS = "infoCursoPeriodoCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_ASIGNACIONES, CACHE_CURSOS);
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(500));
        return cacheManager;
    }
}

