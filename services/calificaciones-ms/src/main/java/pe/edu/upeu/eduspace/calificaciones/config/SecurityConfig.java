package pe.edu.upeu.eduspace.calificaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/internal/**").denyAll()

                // Criterios de evaluación y registro de notas
                .requestMatchers(HttpMethod.POST, "/api/v1/calificaciones/criterios/**").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/calificaciones/criterios/**").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/calificaciones/criterios/**").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/calificaciones/criterios/**").hasAnyRole("DOCENTE", "ADMIN", "SECRETARIA")

                .requestMatchers(HttpMethod.PUT, "/api/v1/calificaciones/notas/**").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers("/api/v1/calificaciones/registro", "/api/v1/calificaciones/pendientes-asignar").hasAnyRole("DOCENTE", "ADMIN")

                // Cierre y reapertura de periodos
                .requestMatchers(HttpMethod.POST, "/api/v1/calificaciones/periodos/cerrar").hasAnyRole("ADMIN", "SECRETARIA")
                .requestMatchers(HttpMethod.POST, "/api/v1/calificaciones/periodos/reabrir").hasRole("ADMIN")

                // Boletines
                .requestMatchers(HttpMethod.POST, "/api/v1/calificaciones/boletines/generar").hasAnyRole("SECRETARIA", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/calificaciones/boletines/**").hasAnyRole("SECRETARIA", "ADMIN", "DOCENTE", "ALUMNO", "APODERADO")

                // Consultas y libretas
                .requestMatchers("/api/v1/calificaciones/alumnos/*/libreta").hasAnyRole("ALUMNO", "APODERADO", "DOCENTE", "SECRETARIA", "ADMIN")
                .requestMatchers("/api/v1/calificaciones/mias").hasAnyRole("APODERADO", "ALUMNO")
                .requestMatchers("/api/v1/calificaciones/promedios", "/api/v1/calificaciones/resultados-anuales").hasAnyRole("DOCENTE", "SECRETARIA", "ADMIN")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }

    public static class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new HashSet<>();
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()));
                }
            }
            List<String> directRoles = jwt.getClaimAsStringList("roles");
            if (directRoles != null) {
                for (String role : directRoles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                }
            }
            return authorities;
        }
    }
}

