package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.EscalaLiteral;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface EscalaLiteralRepository extends JpaRepository<EscalaLiteral, Long> {

    List<EscalaLiteral> findByNivelCodigoOrderByNotaMinDesc(String nivelCodigo);

    @Query("SELECT e FROM EscalaLiteral e WHERE e.nivelCodigo = :nivelCodigo AND :nota >= e.notaMin AND :nota <= e.notaMax")
    Optional<EscalaLiteral> findByNivelCodigoAndNota(@Param("nivelCodigo") String nivelCodigo, @Param("nota") BigDecimal nota);
}

