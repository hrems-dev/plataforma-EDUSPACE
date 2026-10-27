package pe.edu.upeu.eduspace.examenes.config;

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

                // Banco de preguntas
                .requestMatchers("/api/v1/examenes/preguntas/**").hasAnyRole("DOCENTE", "ADMIN")

                // Alumno rindiendo exámenes
                .requestMatchers("/api/v1/examenes/disponibles").hasRole("ALUMNO")
                .requestMatchers(HttpMethod.POST, "/api/v1/examenes/*/intentos").hasRole("ALUMNO")
                .requestMatchers(HttpMethod.PUT, "/api/v1/examenes/intentos/*/respuestas/*").hasRole("ALUMNO")
                .requestMatchers(HttpMethod.POST, "/api/v1/examenes/intentos/*/enviar").hasRole("ALUMNO")
                .requestMatchers(HttpMethod.GET, "/api/v1/examenes/intentos/*/resultado").hasAnyRole("ALUMNO", "APODERADO", "DOCENTE", "ADMIN")

                // Apoderado
                .requestMatchers("/api/v1/examenes/mias").hasAnyRole("APODERADO", "ALUMNO")

                // Docente: gestión, resultados, revisión de abiertas
                .requestMatchers("/api/v1/examenes/*/resultados", "/api/v1/examenes/*/pendientes-revision", "/api/v1/examenes/respuestas/*/revisar").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/examenes").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/examenes/*").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/examenes/*/publicar", "/api/v1/examenes/*/cerrar").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/examenes", "/api/v1/examenes/*").hasAnyRole("DOCENTE", "ADMIN", "SECRETARIA")

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

