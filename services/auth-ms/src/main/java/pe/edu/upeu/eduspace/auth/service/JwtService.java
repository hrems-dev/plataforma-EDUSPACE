package pe.edu.upeu.eduspace.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.auth.config.AuthProperties;
import pe.edu.upeu.eduspace.auth.entity.Rol;
import pe.edu.upeu.eduspace.auth.entity.Usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Emite el access token RS256 con el contrato que esperan todos los servicios:
 * sub, iss, iat, exp, preferred_username, email, usuarioId y realm_access.roles (sin prefijo ROLE_).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder encoder;
    private final AuthProperties props;
    /** La clave que firma: su kid es el que debe ir en la cabecera (en DEV cambia en cada arranque). */
    private final com.nimbusds.jose.jwk.RSAKey claveActual;

    public String emitir(Usuario u) {
        Instant ahora = Instant.now();
        List<String> roles = u.getRoles().stream().map(Rol::getNombre).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(props.getIssuer())
            .subject(String.valueOf(u.getId()))
            .issuedAt(ahora)
            .expiresAt(ahora.plus(vigencia()))
            .claim("preferred_username", u.getUsername())
            .claim("email", u.getEmail())
            .claim("usuarioId", u.getId())
            .claim("realm_access", Map.of("roles", roles))
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(claveActual.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration vigencia() {
        return Duration.ofMinutes(props.getAccessTokenMinutos());
    }
}
