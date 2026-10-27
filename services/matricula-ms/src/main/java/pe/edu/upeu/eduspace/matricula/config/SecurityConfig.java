package pe.edu.upeu.eduspace.matricula.config;

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
                .requestMatchers("/internal/**").permitAll()

                // Campañas
                .requestMatchers(HttpMethod.GET, "/api/v1/matriculas/campanias/**").hasAnyRole("ADMIN", "SECRETARIA", "DOCENTE")
                .requestMatchers("/api/v1/matriculas/campanias/**").hasRole("ADMIN")

                // Apoderado / alumno: solo lo propio (el filtro fino está en el servicio)
                .requestMatchers(HttpMethod.GET, "/api/v1/matriculas/mias").hasAnyRole("APODERADO", "ALUMNO")

                // Lectura de staff (antes del comodín /* para que /cupos no se confunda con un id)
                .requestMatchers(HttpMethod.GET, "/api/v1/matriculas", "/api/v1/matriculas/seccion/*/alumnos").hasAnyRole("ADMIN", "SECRETARIA", "DOCENTE")
                .requestMatchers(HttpMethod.GET, "/api/v1/matriculas/cupos").hasAnyRole("ADMIN", "SECRETARIA")
                .requestMatchers(HttpMethod.GET, "/api/v1/matriculas/*").hasAnyRole("ADMIN", "SECRETARIA", "DOCENTE", "APODERADO", "ALUMNO")

                // Escritura
                .requestMatchers("/api/v1/matriculas/**").hasAnyRole("ADMIN", "SECRETARIA")
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
