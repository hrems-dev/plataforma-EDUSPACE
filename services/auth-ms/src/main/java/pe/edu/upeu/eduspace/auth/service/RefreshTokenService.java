package pe.edu.upeu.eduspace.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.auth.config.AuthProperties;
import pe.edu.upeu.eduspace.auth.entity.RefreshToken;
import pe.edu.upeu.eduspace.auth.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** El token se entrega una sola vez; en la base solo vive su SHA-256. Se rota en cada uso. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final AuthProperties props;

    @Transactional
    public String crear(Long usuarioId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repo.save(RefreshToken.builder().usuarioId(usuarioId).tokenHash(hash(token))
            .expiraEn(LocalDateTime.now().plusDays(props.getRefreshTokenDias())).build());
        return token;
    }

    /**
     * Consume el token: si es válido lo revoca y devuelve el usuario dueño. Si se presenta uno ya revocado
     * (posible robo) se revocan TODAS las sesiones del usuario.
     */
    @Transactional(noRollbackFor = pe.edu.upeu.eduspace.auth.exception.CredencialesInvalidasException.class)
    public Optional<Long> consumir(String token) {
        Optional<RefreshToken> opt = repo.findByTokenHash(hash(token));
        if (opt.isEmpty()) return Optional.empty();
        RefreshToken t = opt.get();
        if (Boolean.TRUE.equals(t.getRevocado())) {
            log.warn("Refresh token revocado reutilizado (usuario {}): se cierran todas sus sesiones", t.getUsuarioId());
            repo.revocarTodos(t.getUsuarioId());
            return Optional.empty();
        }
        if (t.getExpiraEn().isBefore(LocalDateTime.now())) return Optional.empty();
        t.setRevocado(true);
        repo.save(t);
        return Optional.of(t.getUsuarioId());
    }

    @Transactional
    public void revocar(String token) {
        repo.findByTokenHash(hash(token)).ifPresent(t -> {
            t.setRevocado(true);
            repo.save(t);
        });
    }

    @Transactional
    public void revocarTodos(Long usuarioId) {
        repo.revocarTodos(usuarioId);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    @Transactional
    public void limpiarVencidos() {
        int n = repo.borrarVencidos(LocalDateTime.now().minusDays(1));
        if (n > 0) log.info("{} refresh token(s) vencidos eliminados", n);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
