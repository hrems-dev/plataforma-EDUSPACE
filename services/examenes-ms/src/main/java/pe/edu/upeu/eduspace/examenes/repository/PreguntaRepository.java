package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.Pregunta;

import java.util.List;

@Repository
public interface PreguntaRepository extends JpaRepository<Pregunta, Long> {

    /** {@code tema} nunca es nulo (texto vacío = sin filtro): PostgreSQL no infiere el tipo de un parámetro nulo en LIKE. */
    @Query("""
        SELECT p FROM Pregunta p
        WHERE p.cursoId = :cursoId
          AND (:docenteId IS NULL OR p.docenteId = :docenteId)
          AND (:tema = '' OR LOWER(p.tema) LIKE LOWER(CONCAT('%', :tema, '%')))
          AND (:tipo IS NULL OR p.tipo = :tipo)
          AND p.activa = true
        ORDER BY p.creadaEn DESC
    """)
    Page<Pregunta> findBancoPreguntas(@Param("cursoId") Long cursoId,
                                      @Param("docenteId") Long docenteId,
                                      @Param("tema") String tema,
                                      @Param("tipo") String tipo,
                                      Pageable pageable);

    List<Pregunta> findByCursoIdAndActivaTrue(Long cursoId);

    @Query("SELECT COUNT(ep) > 0 FROM ExamenPregunta ep WHERE ep.pregunta.id = :preguntaId AND ep.examen.estado != 'BORRADOR'")
    boolean isPreguntaEnUsoEnExamenPublicado(@Param("preguntaId") Long preguntaId);
}

