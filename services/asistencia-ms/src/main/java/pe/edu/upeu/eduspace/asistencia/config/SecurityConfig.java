package pe.edu.upeu.eduspace.asistencia.config;

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

                // Alumno / apoderado: solo lo propio (filtro fino en el servicio)
                .requestMatchers(HttpMethod.GET, "/api/v1/asistencia/mias", "/api/v1/asistencia/justificaciones/mias").hasAnyRole("APODERADO", "ALUMNO")
                .requestMatchers(HttpMethod.GET, "/api/v1/asistencia/alumnos/*/resumen").hasAnyRole("ADMIN", "SECRETARIA", "DOCENTE", "APODERADO", "ALUMNO")
                .requestMatchers(HttpMethod.POST, "/api/v1/asistencia/justificaciones", "/api/v1/asistencia/justificaciones/*/archivo-url").hasRole("APODERADO")
                .requestMatchers(HttpMethod.GET, "/api/v1/asistencia/justificaciones/*/archivo").hasAnyRole("ADMIN", "SECRETARIA", "APODERADO", "ALUMNO")

                // Secretaría
                .requestMatchers(HttpMethod.GET, "/api/v1/asistencia/justificaciones").hasAnyRole("ADMIN", "SECRETARIA")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/asistencia/justificaciones/*/resolver", "/api/v1/asistencia/detalle/*").hasAnyRole("ADMIN", "SECRETARIA")

                // Docente
                .requestMatchers(HttpMethod.POST, "/api/v1/asistencia/sesiones").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/asistencia/sesiones/*/registro").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/asistencia/sesiones/*/cerrar").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/asistencia/sesiones/**", "/api/v1/asistencia/secciones/*/reporte").hasAnyRole("DOCENTE", "SECRETARIA", "ADMIN")
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
            if (jwt.getClaim("realm_access") instanceof Map<?, ?> ra && ra.get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()));
                }
            }
            List<String> directRoles = jwt.getClaimAsStringList("roles");
            if (directRoles != null) {
                for (String role : directRoles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase().replace("ROLE_", "")));
                }
            }
            return authorities;
        }
    }
}
