package pe.edu.upeu.eduspace.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.auth.config.AuthProperties;
import pe.edu.upeu.eduspace.auth.dto.Dtos.TokenResponse;
import pe.edu.upeu.eduspace.auth.entity.Usuario;
import pe.edu.upeu.eduspace.auth.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.auth.exception.CredencialesInvalidasException;
import pe.edu.upeu.eduspace.auth.exception.UsuarioBloqueadoException;
import pe.edu.upeu.eduspace.auth.repository.UsuarioRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    static final String MENSAJE_CREDENCIALES = "Usuario o clave incorrectos.";
    /** Hash de relleno para que "usuario inexistente" tarde lo mismo que "clave errónea". */
    private static final String HASH_RELLENO = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("relleno-no-valido");

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final AuthProperties props;

    /** Los intentos fallidos se persisten aunque se lance la excepción. */
    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, UsuarioBloqueadoException.class})
    public TokenResponse login(String identificador, String clave) {
        Usuario u = usuarios.findByUsernameOrEmail(identificador.trim()).orElse(null);
        if (u == null) {
            encoder.matches(clave, HASH_RELLENO);
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }
        if (u.estaBloqueado()) {
            long min = Math.max(1, Duration.between(LocalDateTime.now(), u.getBloqueadoHasta()).toMinutes() + 1);
            throw new UsuarioBloqueadoException("Usuario bloqueado por intentos fallidos. Intente de nuevo en " + min + " minuto(s).");
        }
        if (!encoder.matches(clave, u.getPasswordHash()) || !Boolean.TRUE.equals(u.getActivo())) {
            registrarFallo(u);
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }
        u.setIntentosFallidos((short) 0);
        u.setBloqueadoHasta(null);
        u.setUltimoLogin(LocalDateTime.now());
        usuarios.save(u);
        return emitir(u);
    }

    /** Sin rollback ante credenciales inválidas: la revocación de sesiones por reutilización debe persistir. */
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public TokenResponse refrescar(String refreshToken) {
        Long usuarioId = refresh.consumir(refreshToken).orElseThrow(() -> new CredencialesInvalidasException("Sesión inválida o vencida."));
        Usuario u = usuarios.findById(usuarioId).filter(x -> Boolean.TRUE.equals(x.getActivo()) && !x.estaBloqueado())
            .orElseThrow(() -> new CredencialesInvalidasException("Sesión inválida o vencida."));
        return emitir(u);
    }

    @Transactional
    public void logout(String refreshToken) {
        refresh.revocar(refreshToken);
    }

    @Transactional
    public void cambiarClave(Long usuarioId, String actual, String nueva) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new CredencialesInvalidasException("Sesión inválida."));
        if (!encoder.matches(actual, u.getPasswordHash())) {
            throw new BusinessRuleException("La clave actual es incorrecta.");
        }
        validarClave(nueva);
        if (encoder.matches(nueva, u.getPasswordHash())) {
            throw new BusinessRuleException("La clave nueva debe ser distinta de la actual.");
        }
        u.setPasswordHash(encoder.encode(nueva));
        u.setDebeCambiarClave(false);
        u.setActualizadoEn(LocalDateTime.now());
        usuarios.save(u);
        refresh.revocarTodos(usuarioId);        // cierra las demás sesiones
    }

    void validarClave(String clave) {
        if (clave == null || clave.length() < props.getClaveMinima()) {
            throw new BusinessRuleException("La clave debe tener al menos " + props.getClaveMinima() + " caracteres.");
        }
        if (clave.length() > 72) throw new BusinessRuleException("La clave no puede superar 72 caracteres.");
        if (clave.chars().noneMatch(Character::isLetter) || clave.chars().noneMatch(Character::isDigit)) {
            throw new BusinessRuleException("La clave debe combinar letras y números.");
        }
    }

    private void registrarFallo(Usuario u) {
        int n = u.getIntentosFallidos() + 1;
        u.setIntentosFallidos((short) n);
        if (n >= props.getMaxIntentosFallidos()) {
            u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(props.getBloqueoMinutos()));
            u.setIntentosFallidos((short) 0);
            log.warn("Usuario {} bloqueado {} min tras {} intentos fallidos", u.getUsername(), props.getBloqueoMinutos(), n);
        }
        usuarios.save(u);
    }

    private TokenResponse emitir(Usuario u) {
        return new TokenResponse(jwt.emitir(u), refresh.crear(u.getId()), "Bearer", jwt.vigencia().toSeconds(),
            Boolean.TRUE.equals(u.getDebeCambiarClave()));
    }
}
