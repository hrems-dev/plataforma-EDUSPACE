package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.matricula.entity.Matricula;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    @Query("SELECT m FROM Matricula m WHERE m.alumnoId = :alumnoId AND m.anioId = :anioId "
         + "AND m.estado IN ('PRE_MATRICULA','POR_APROBAR','MATRICULADO')")
    Optional<Matricula> findVivaByAlumnoAndAnio(@Param("alumnoId") Long alumnoId, @Param("anioId") Long anioId);

    List<Matricula> findByAlumnoIdOrderByAnioIdDesc(Long alumnoId);

    Optional<Matricula> findByCodigo(String codigo);

    List<Matricula> findByAlumnoIdIn(Collection<Long> alumnoIds);

    List<Matricula> findBySeccionIdAndEstadoIn(Long seccionId, Collection<String> estados);

    long countBySeccionIdAndEstado(Long seccionId, String estado);

    @Query("SELECT m FROM Matricula m WHERE m.estado = 'PRE_MATRICULA' AND m.venceEn < :ahora")
    List<Matricula> findPreMatriculasVencidas(@Param("ahora") LocalDateTime ahora);

    /** {@code texto} nunca es nulo (texto vacío = sin filtro): PostgreSQL no infiere el tipo de un parámetro nulo en LIKE. */
    @Query("""
        SELECT m FROM Matricula m
        WHERE (:anioId IS NULL OR m.anioId = :anioId)
          AND (:gradoId IS NULL OR m.gradoId = :gradoId)
          AND (:seccionId IS NULL OR m.seccionId = :seccionId)
          AND (:estado IS NULL OR m.estado = :estado)
          AND (:texto = '' OR LOWER(m.alumnoNombre) LIKE LOWER(CONCAT('%', :texto, '%')) OR LOWER(m.codigo) LIKE LOWER(CONCAT('%', :texto, '%')))
        """)
    Page<Matricula> buscar(@Param("anioId") Long anioId, @Param("gradoId") Long gradoId, @Param("seccionId") Long seccionId,
                           @Param("estado") String estado, @Param("texto") String texto, Pageable pageable);

    @Query(value = "SELECT nextval('matricula_codigo_seq')", nativeQuery = true)
    Long siguienteCorrelativo();
}
