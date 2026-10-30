package pe.edu.eduspace.notificaciones.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Resource server (JWT RS256 de auth-ms). La bandeja, las preferencias y los dispositivos los puede usar
 * cualquier usuario autenticado sobre sus propios datos; plantillas y envío manual son solo ADMIN.
 */
@Configuration
public class SecurityConfig {

    @Value("${app.security.swagger-public:true}")
    private boolean swaggerPublic;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll();
                    if (swaggerPublic) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    auth.requestMatchers("/internal/**").permitAll();   // protegido por ServiceTokenFilter (X-Service-Token)
                    auth.requestMatchers("/api/v1/notificaciones/plantillas/**", "/api/v1/notificaciones/enviar", "/api/v1/notificaciones/admin/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RealmRolesConverter());
        return converter;
    }

    /** Convierte {@code realm_access.roles} en autoridades con prefijo {@code ROLE_}. */
    static class RealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Object realm = jwt.getClaim("realm_access");
            if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
                return roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
            }
            return List.of();
        }
    }
}
