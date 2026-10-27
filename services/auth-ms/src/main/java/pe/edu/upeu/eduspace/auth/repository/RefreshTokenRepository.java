package pe.edu.upeu.eduspace.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.auth.entity.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revocado = true WHERE t.usuarioId = :usuarioId AND t.revocado = false")
    int revocarTodos(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiraEn < :limite")
    int borrarVencidos(@Param("limite") LocalDateTime limite);
}
