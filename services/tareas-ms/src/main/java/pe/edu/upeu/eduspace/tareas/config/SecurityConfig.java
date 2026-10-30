package pe.edu.upeu.eduspace.tareas.config;

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

                // Materiales del curso: el docente sube y borra; los demás los ven si participan (filtro fino en el servicio)
                .requestMatchers(HttpMethod.POST, "/api/v1/tareas/materiales", "/api/v1/tareas/materiales/archivo-url").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tareas/materiales/*").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/tareas/materiales", "/api/v1/tareas/materiales/*/url").authenticated()

                // Alumno / apoderado
                .requestMatchers(HttpMethod.GET, "/api/v1/tareas/mias", "/api/v1/tareas/mias/pendientes").hasAnyRole("ALUMNO", "APODERADO")
                .requestMatchers(HttpMethod.POST, "/api/v1/tareas/*/entregas", "/api/v1/tareas/*/entregas/archivo-url").hasRole("ALUMNO")

                // Docente
                .requestMatchers(HttpMethod.POST, "/api/v1/tareas", "/api/v1/tareas/*/adjuntos/url").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tareas/*").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/tareas/*/publicar", "/api/v1/tareas/*/cerrar", "/api/v1/tareas/*/calificaciones",
                    "/api/v1/tareas/entregas/*/calificar", "/api/v1/tareas/entregas/*/devolver").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/tareas/*/entregas").hasAnyRole("DOCENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/tareas").hasAnyRole("DOCENTE", "SECRETARIA", "ADMIN")

                // Detalle y descargas: participantes (filtro fino en el controlador)
                .requestMatchers(HttpMethod.GET, "/api/v1/tareas/*", "/api/v1/tareas/*/adjuntos/*/url",
                    "/api/v1/tareas/entregas/*/archivos/*/url").authenticated()
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
