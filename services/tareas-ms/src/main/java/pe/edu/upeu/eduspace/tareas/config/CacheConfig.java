package pe.edu.upeu.eduspace.tareas.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String CACHE_ASIGNACIONES = "asignacionesCache";   // 60 s
    public static final String CACHE_PERIODOS = "periodosCache";           // 5 min
    public static final String CACHE_HIJOS = "hijosCache";                 // 5 min
    public static final String CACHE_DOCENTE = "docenteCache";             // 5 min

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager m = new SimpleCacheManager();
        m.setCaches(List.of(
            cache(CACHE_ASIGNACIONES, 60, TimeUnit.SECONDS),
            cache(CACHE_PERIODOS, 5, TimeUnit.MINUTES),
            cache(CACHE_HIJOS, 5, TimeUnit.MINUTES),
            cache(CACHE_DOCENTE, 5, TimeUnit.MINUTES)));
        return m;
    }

    private CaffeineCache cache(String nombre, long t, TimeUnit u) {
        return new CaffeineCache(nombre, Caffeine.newBuilder().expireAfterWrite(t, u).maximumSize(2000).build());
    }
}
