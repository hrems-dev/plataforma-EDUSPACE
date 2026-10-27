package pe.edu.upeu.eduspace.calificaciones.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_ASIGNACIONES = "asignacionesDocenteCache";
    public static final String CACHE_PLANIFICACION = "planificacionInfoCache";
    public static final String CACHE_ALUMNOS = "alumnosNombresCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            CACHE_ASIGNACIONES,
            CACHE_PLANIFICACION,
            CACHE_ALUMNOS
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000));
        return cacheManager;
    }
}

