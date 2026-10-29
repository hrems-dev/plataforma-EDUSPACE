package pe.edu.eduspace.planificacion.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.planificacion.entity.Horario;

public interface HorarioRepository extends JpaRepository<Horario, Long> {

    @Query("""
            select h from Horario h
            where h.asignacion.seccion.id = :seccionId
            order by h.diaSemana, h.bloque.orden
            """)
    List<Horario> findBySeccion(@Param("seccionId") Long seccionId);

    @Query("""
            select h from Horario h
            where h.asignacion.docente.id = :docenteId
            order by h.diaSemana, h.bloque.turno, h.bloque.orden
            """)
    List<Horario> findByDocente(@Param("docenteId") Long docenteId);

    /** Choque de docente: mismo docente, día y bloque (en cualquier sección). */
    @Query("""
            select count(h) > 0 from Horario h
            where h.asignacion.docente.id = :docenteId and h.diaSemana = :dia and h.bloque.id = :bloqueId
              and (:excluirId is null or h.id <> :excluirId)
            """)
    boolean existsChoqueDocente(@Param("docenteId") Long docenteId, @Param("dia") Short dia,
                                @Param("bloqueId") Long bloqueId, @Param("excluirId") Long excluirId);

    @Query("""
            select count(h) > 0 from Horario h
            where h.aula.id = :aulaId and h.diaSemana = :dia and h.bloque.id = :bloqueId
              and (:excluirId is null or h.id <> :excluirId)
            """)
    boolean existsChoqueAula(@Param("aulaId") Long aulaId, @Param("dia") Short dia,
                             @Param("bloqueId") Long bloqueId, @Param("excluirId") Long excluirId);

    @Query("""
            select count(h) > 0 from Horario h
            where h.asignacion.seccion.id = :seccionId and h.diaSemana = :dia and h.bloque.id = :bloqueId
              and (:excluirId is null or h.id <> :excluirId)
            """)
    boolean existsChoqueSeccion(@Param("seccionId") Long seccionId, @Param("dia") Short dia,
                                @Param("bloqueId") Long bloqueId, @Param("excluirId") Long excluirId);

    /** Todas las clases de las secciones del año (el generador las usa como espacios ya ocupados). */
    @Query("select h from Horario h where h.asignacion.seccion.anio.id = :anioId")
    List<Horario> findByAnio(@Param("anioId") Long anioId);

    long countByAsignacionId(Long asignacionId);

    @Query("select count(h) from Horario h where h.asignacion.id = :asignacionId and (:excluirId is null or h.id <> :excluirId)")
    long countByAsignacionExcluyendo(@Param("asignacionId") Long asignacionId, @Param("excluirId") Long excluirId);

    boolean existsByAulaId(Long aulaId);

    boolean existsByBloqueId(Long bloqueId);
}
