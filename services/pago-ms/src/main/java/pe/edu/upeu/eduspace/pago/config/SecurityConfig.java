package pe.edu.upeu.eduspace.pago.config;

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
import java.util.stream.Collectors;

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
                .requestMatchers("/api/v1/pagos/alertas/**").hasAnyRole("ADMIN", "SECRETARIA")
                
                // Endpoints de caja
                .requestMatchers(HttpMethod.POST, "/api/v1/pagos/caja/abrir", "/api/v1/pagos/caja/cerrar").hasRole("CAJERO")
                .requestMatchers(HttpMethod.GET, "/api/v1/pagos/caja/actual").hasRole("CAJERO")
                .requestMatchers(HttpMethod.GET, "/api/v1/pagos/caja/*/reporte").hasAnyRole("CAJERO", "ADMIN")
                
                // Catálogos: conceptos, tarifas, descuentos
                .requestMatchers(HttpMethod.GET, "/api/v1/pagos/conceptos/**", "/api/v1/pagos/tarifas/**", "/api/v1/pagos/descuentos/**").hasAnyRole("ADMIN", "SECRETARIA")
                .requestMatchers(HttpMethod.POST, "/api/v1/pagos/conceptos/**", "/api/v1/pagos/tarifas/**", "/api/v1/pagos/descuentos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/pagos/conceptos/**", "/api/v1/pagos/tarifas/**", "/api/v1/pagos/descuentos/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/pagos/descuentos/asignar").hasRole("ADMIN")

                // Pagos
                .requestMatchers(HttpMethod.POST, "/api/v1/pagos/online").hasRole("APODERADO")
                .requestMatchers(HttpMethod.POST, "/api/v1/pagos").hasAnyRole("CAJERO", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pagos/*/anular").hasRole("ADMIN")
                
                // Consultas y reportes
                .requestMatchers("/api/v1/pagos/morosos/**", "/api/v1/pagos/reportes/**").hasAnyRole("ADMIN", "SECRETARIA")
                .requestMatchers("/api/v1/pagos/mias/**").hasRole("APODERADO")
                .requestMatchers("/api/v1/pagos/alumnos/*/estado-cuenta").hasAnyRole("CAJERO", "SECRETARIA", "ADMIN", "APODERADO", "ALUMNO")
                .requestMatchers(HttpMethod.GET, "/api/v1/pagos/*/comprobante").hasAnyRole("CAJERO", "ADMIN", "APODERADO", "ALUMNO", "SECRETARIA")
                .requestMatchers(HttpMethod.GET, "/api/v1/pagos/**").hasAnyRole("CAJERO", "SECRETARIA", "ADMIN", "APODERADO")

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

            // 1. Check realm_access.roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()));
                }
            }

            // 2. Check direct roles claim
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

