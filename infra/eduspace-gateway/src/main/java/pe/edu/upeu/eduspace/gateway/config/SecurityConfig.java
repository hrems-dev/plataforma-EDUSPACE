package pe.edu.upeu.eduspace.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * El Gateway valida la firma y la vigencia del JWT (RS256, claves de auth-ms) antes de enrutar.
 * Los roles los sigue evaluando cada servicio. Solo login/refresh/logout y /actuator/health pasan sin token (el resto de actuator, solo en DEV).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${eduspace.gateway.actuator-public:false}") boolean actuatorPublic) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                if (actuatorPublic) {
                    auth.requestMatchers("/actuator/**").permitAll();
                }
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();
                auth.requestMatchers("/api/v1/publico/**").permitAll();   // matrícula en línea del sitio público (limitada por RateLimitFilter)
                auth.anyRequest().authenticated();   // incluye el resto de /actuator cuando no es público
            })
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${eduspace.gateway.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList();
        // Patrones: aceptan origen exacto o comodines (p. ej. http://192.168.*.*:4200 para abrir la web desde otro equipo de la red en DEV).
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Trace-ID", "X-Correlation-Id", "X-Matricula-Token"));
        config.setExposedHeaders(List.of("X-Trace-ID", "X-Correlation-Id", "Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
