package pe.edu.upeu.eduspace.matricula.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String CACHE_SECCION = "seccionCache";
    public static final String CACHE_ANIO_ACTIVO = "anioActivoCache";
    public static final String CACHE_GRADO = "gradoCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager m = new CaffeineCacheManager(CACHE_SECCION, CACHE_ANIO_ACTIVO, CACHE_GRADO);
        m.setCaffeine(Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1000));
        return m;
    }
}
