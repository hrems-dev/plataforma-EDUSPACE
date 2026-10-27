package pe.edu.upeu.eduspace.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "JWKS")
public class JwksController {

    private final JWKSet jwksPublico;

    @GetMapping(value = "/.well-known/jwks.json", produces = "application/json")
    @Operation(summary = "Claves públicas para validar los tokens (solo parte pública)")
    public Map<String, Object> jwks() {
        return jwksPublico.toJSONObject(true);
    }
}
